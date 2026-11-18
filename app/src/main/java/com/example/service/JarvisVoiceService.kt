package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class JarvisVoiceService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isActive = false
    private var awaitingCommandAfterWakeWord = false
    private var languageLocale = "en-IN"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        languageLocale = intent?.getStringExtra(EXTRA_LOCALE) ?: "en-IN"
        createChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isActive = true
        _serviceRunning.value = true
        scheduleNextCycle(delayMs = 250L)
        return START_STICKY
    }

    private fun scheduleNextCycle(delayMs: Long) {
        mainHandler.removeCallbacksAndMessages(null)
        if (!isActive) return
        mainHandler.postDelayed({ startWakeWordCycle() }, delayMs)
    }

    private fun startWakeWordCycle() {
        if (!isActive || !SpeechRecognizer.isRecognitionAvailable(this)) return
        destroyRecognizer()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer = recognizer

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onError(error: Int) {
                if (isActive) {
                    scheduleNextCycle(delayMs = 900L)
                }
            }

            override fun onResults(results: Bundle?) {
                val spoken = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                    .trim()
                handleSpokenResult(spoken)
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageLocale)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            recognizer.startListening(intent)
        } catch (_: Exception) {
            scheduleNextCycle(delayMs = 1500L)
        }
    }

    private fun handleSpokenResult(spoken: String) {
        if (spoken.isBlank()) {
            scheduleNextCycle(delayMs = 500L)
            return
        }
        val lower = spoken.lowercase()
        val hasWakeWord = lower.contains("hey jarvis") ||
            lower.contains("ok jarvis") ||
            lower.contains("jarvis") ||
            lower.contains("हे जार्विस") ||
            lower.contains("जार्विस")

        if (awaitingCommandAfterWakeWord) {
            awaitingCommandAfterWakeWord = false
            _wakeCommands.tryEmit(spoken)
            scheduleNextCycle(delayMs = 1200L)
            return
        }

        if (hasWakeWord) {
            val remainder = spoken
                .replace(Regex(".*?(?:hey\\s+jarvis|ok\\s+jarvis|jarvis|हे\\s+जार्विस|जार्विस)[,:\\s]*", RegexOption.IGNORE_CASE), "")
                .trim()
            if (remainder.length >= 2) {
                _wakeCommands.tryEmit(spoken)
                scheduleNextCycle(delayMs = 1200L)
            } else {
                awaitingCommandAfterWakeWord = true
                _wakeWordTriggered.tryEmit(Unit)
                scheduleNextCycle(delayMs = 800L)
            }
        } else {
            scheduleNextCycle(delayMs = 500L)
        }
    }

    private fun destroyRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        } finally {
            speechRecognizer = null
        }
    }

    override fun onDestroy() {
        isActive = false
        _serviceRunning.value = false
        mainHandler.removeCallbacksAndMessages(null)
        destroyRecognizer()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.voice_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.voice_channel_desc)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, JarvisVoiceService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Jarvis VIP • Hands-Free Active")
            .setContentText("Listening for \"Hey JARVIS\" • Tap Disable to pause")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .addAction(0, "Disable Mic", stopIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "jarvis_voice_core_channel"
        private const val NOTIFICATION_ID = 4109
        const val ACTION_STOP = "com.example.action.STOP_VOICE_SERVICE"
        const val EXTRA_LOCALE = "extra_locale"

        private val _serviceRunning = MutableStateFlow(false)
        val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

        private val _wakeCommands = MutableSharedFlow<String>(extraBufferCapacity = 8)
        val wakeCommands: SharedFlow<String> = _wakeCommands.asSharedFlow()

        private val _wakeWordTriggered = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
        val wakeWordTriggered: SharedFlow<Unit> = _wakeWordTriggered.asSharedFlow()

        fun start(context: Context, locale: String) {
            val intent = Intent(context, JarvisVoiceService::class.java).apply {
                putExtra(EXTRA_LOCALE, locale)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, JarvisVoiceService::class.java))
        }
    }
}
