package com.example.ui.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.LocalSecretConfig
import com.example.data.local.SecurePreferencesStore
import com.example.data.model.ActionLog
import com.example.data.model.ActionType
import com.example.data.model.AssistantCoreState
import com.example.data.model.AssistantTask
import com.example.data.model.ChatMessage
import com.example.data.model.ContactMatch
import com.example.data.model.DeviceTelemetry
import com.example.data.model.InstalledAppInfo
import com.example.data.model.LanguageMode
import com.example.data.model.NewsHeadline
import com.example.data.model.NotificationDigestItem
import com.example.data.model.PendingAction
import com.example.data.model.TaskCategory
import com.example.data.model.TaskPriority
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.WeatherInfo
import com.example.data.remote.BrainResponse
import com.example.data.remote.LiveAssistantInfoClient
import com.example.data.remote.OpenAiBrainClient
import com.example.data.repository.JarvisFirestoreRepository
import com.example.domain.InfoQuery
import com.example.domain.JarvisCommandEngine
import com.example.platform.DeviceControlManager
import com.example.platform.JarvisVoiceEngine
import com.example.service.JarvisAccessibilityService
import com.example.service.JarvisNotificationListenerService
import com.example.service.JarvisVoiceService
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UiEvent {
    data class Snackbar(val message: String) : UiEvent
    data object RequestPhotoPicker : UiEvent
    data object RequestCameraCapture : UiEvent
    data class RequestRuntimePermission(val permission: String, val rationale: String) : UiEvent
}

class JarvisViewModel(
    private val appContext: Context,
    private val currentUserId: String,
    private val userDisplayName: String,
    private val userEmail: String,
    private val repository: JarvisFirestoreRepository,
    private val secretStore: SecurePreferencesStore,
    private val deviceControl: DeviceControlManager,
    private val voiceEngine: JarvisVoiceEngine,
    private val openAiClient: OpenAiBrainClient = OpenAiBrainClient(),
    private val infoClient: LiveAssistantInfoClient = LiveAssistantInfoClient()
) : ViewModel() {

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId)
        .catch { error ->
            unwrapFirestoreError(error)
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val secretConfig: StateFlow<LocalSecretConfig> = secretStore.configFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), LocalSecretConfig())

    val chatMessagesState: StateFlow<UiState<List<ChatMessage>>> = repository.observeChatMessages(currentUserId)
        .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
        .catch { error ->
            val msg = unwrapFirestoreError(error)
            emit(UiState.Error(msg))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val tasksState: StateFlow<UiState<List<AssistantTask>>> = repository.observeTasks(currentUserId)
        .map<List<AssistantTask>, UiState<List<AssistantTask>>> { UiState.Success(it) }
        .catch { error ->
            val msg = unwrapFirestoreError(error)
            emit(UiState.Error(msg))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val actionLogsState: StateFlow<UiState<List<ActionLog>>> = repository.observeActionLogs(currentUserId)
        .map<List<ActionLog>, UiState<List<ActionLog>>> { UiState.Success(it) }
        .catch { error ->
            val msg = unwrapFirestoreError(error)
            emit(UiState.Error(msg))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val _telemetry = MutableStateFlow(deviceControl.getDeviceTelemetry())
    val telemetry: StateFlow<DeviceTelemetry> = _telemetry.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _contacts = MutableStateFlow<List<ContactMatch>>(emptyList())
    val contacts: StateFlow<List<ContactMatch>> = _contacts.asStateFlow()

    private val _weather = MutableStateFlow<WeatherInfo?>(null)
    val weather: StateFlow<WeatherInfo?> = _weather.asStateFlow()

    private val _news = MutableStateFlow<List<NewsHeadline>>(emptyList())
    val news: StateFlow<List<NewsHeadline>> = _news.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<PendingAction?>(null)
    val pendingConfirmation: StateFlow<PendingAction?> = _pendingConfirmation.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private val _selectedSocialPhotoUri = MutableStateFlow<Uri?>(null)
    val selectedSocialPhotoUri: StateFlow<Uri?> = _selectedSocialPhotoUri.asStateFlow()

    private val _lastCapturedPhotoUri = MutableStateFlow<Uri?>(null)
    val lastCapturedPhotoUri: StateFlow<Uri?> = _lastCapturedPhotoUri.asStateFlow()

    val isListening: StateFlow<Boolean> = voiceEngine.isListening
    val isSpeaking: StateFlow<Boolean> = voiceEngine.isSpeaking
    val rmsLevel: StateFlow<Float> = voiceEngine.rmsLevel
    val partialTranscript: StateFlow<String> = voiceEngine.partialTranscript
    val wakeWordServiceRunning: StateFlow<Boolean> = JarvisVoiceService.serviceRunning
    val notifications: StateFlow<List<NotificationDigestItem>> = JarvisNotificationListenerService.recentNotifications

    val coreState: StateFlow<AssistantCoreState> = combine(
        isListening,
        isSpeaking,
        _isThinking,
        wakeWordServiceRunning
    ) { listening, speaking, thinking, wakeActive ->
        when {
            listening -> AssistantCoreState.LISTENING
            thinking -> AssistantCoreState.THINKING
            speaking -> AssistantCoreState.SPEAKING
            wakeActive -> AssistantCoreState.WAKE_WORD_ACTIVE
            else -> AssistantCoreState.STANDBY
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AssistantCoreState.STANDBY)

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(currentUserId, userDisplayName, userEmail)
            refreshDeviceDiagnostics()
            loadInstalledApps()
            refreshContactsIfPermitted()
            refreshWeather("New Delhi")
        }
        viewModelScope.launch {
            secretConfig.collect { cfg ->
                voiceEngine.updateVoiceTuning(cfg.ttsPitch, cfg.ttsRate)
            }
        }
        viewModelScope.launch {
            JarvisVoiceService.wakeWordTriggered.collect {
                val lang = currentLanguage()
                val greet = if (lang == LanguageMode.HINDI) {
                    "जी, बताइए मैं आपकी क्या मदद कर सकती हूँ?"
                } else {
                    "Yes, I am listening. How can I help?"
                }
                if (userProfile.value?.voiceEnabled != false) {
                    voiceEngine.speak(greet, if (lang == LanguageMode.HINDI) "hi" else "en")
                }
            }
        }
        viewModelScope.launch {
            JarvisVoiceService.wakeCommands.collect { spokenCommand ->
                submitCommand(spokenCommand)
            }
        }
    }

    private fun unwrapFirestoreError(error: Throwable): String {
        val fsErr = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (fsErr == null && error is CancellationException) throw error
        val root = fsErr ?: error
        Log.w("JarvisVM", "Firestore stream error", root)
        return root.message ?: "Unable to sync cloud data"
    }

    private fun currentLanguage(): LanguageMode =
        LanguageMode.fromRaw(userProfile.value?.preferredLanguage)

    fun refreshDeviceDiagnostics() {
        _telemetry.value = deviceControl.getDeviceTelemetry()
    }

    fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _installedApps.value = deviceControl.queryInstalledApps()
        }
    }

    fun refreshContactsIfPermitted(query: String = "") {
        val granted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return
        viewModelScope.launch(Dispatchers.IO) {
            _contacts.value = deviceControl.searchContacts(query)
        }
    }

    fun refreshWeather(city: String) {
        viewModelScope.launch {
            infoClient.fetchWeather(city).onSuccess { _weather.value = it }
        }
    }

    fun refreshNews(hindi: Boolean = currentLanguage() == LanguageMode.HINDI) {
        viewModelScope.launch {
            infoClient.fetchDailyNews(hindi).onSuccess { _news.value = it }
        }
    }

    fun startVoiceInput() {
        val hasMic = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasMic) {
            _events.tryEmit(
                UiEvent.RequestRuntimePermission(
                    Manifest.permission.RECORD_AUDIO,
                    "Microphone permission is required for Hindi and English voice commands."
                )
            )
            return
        }
        voiceEngine.stopSpeaking()
        val localeTag = when (currentLanguage()) {
            LanguageMode.HINDI -> "hi-IN"
            LanguageMode.ENGLISH -> "en-IN"
            LanguageMode.BILINGUAL -> "en-IN"
        }
        voiceEngine.startListening(
            languageLocaleTag = localeTag,
            onResult = { spoken -> submitCommand(spoken) },
            onError = { err -> _events.tryEmit(UiEvent.Snackbar(err)) }
        )
    }

    fun stopVoiceInput() {
        voiceEngine.stopListening()
    }

    fun stopSpeaking() {
        voiceEngine.stopSpeaking()
    }

    fun toggleHandsFreeWakeWord(enable: Boolean) {
        viewModelScope.launch {
            if (enable) {
                val hasMic = ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasMic) {
                    _events.tryEmit(
                        UiEvent.RequestRuntimePermission(
                            Manifest.permission.RECORD_AUDIO,
                            "Microphone permission is needed to listen for 'Hey JARVIS'."
                        )
                    )
                    return@launch
                }
                val locale = if (currentLanguage() == LanguageMode.HINDI) "hi-IN" else "en-IN"
                JarvisVoiceService.start(appContext, locale)
                _events.tryEmit(UiEvent.Snackbar("Hey JARVIS hands-free mode started."))
            } else {
                JarvisVoiceService.stop(appContext)
                _events.tryEmit(UiEvent.Snackbar("Hands-free microphone paused."))
            }
            secretStore.setWakeWordServiceEnabled(enable)
            repository.updateUserPreferences(currentUserId, handsFreeWakeWord = enable)
        }
    }

    fun submitCommand(rawInput: String) {
        val clean = rawInput.trim()
        if (clean.isBlank()) return

        viewModelScope.launch {
            val langTag = JarvisCommandEngine.detectLanguageTag(clean)
            repository.addChatMessage(
                userId = currentUserId,
                role = "user",
                content = clean,
                languageTag = langTag
            )

            _isThinking.value = true
            try {
                val parsed = JarvisCommandEngine.parseCommand(clean)
                if (parsed.infoQuery != null) {
                    handleInfoQuery(clean, parsed.infoQuery, parsed.languageTag)
                } else if (parsed.handledLocally) {
                    var reply = parsed.replyText
                    val actionSummaries = mutableListOf<String>()
                    var status = "executed"

                    for (action in parsed.actions) {
                        if (action.requiresConfirmation) {
                            _pendingConfirmation.value = action
                            status = "pending_confirm"
                            actionSummaries += "Awaiting confirmation: ${action.title}"
                        } else {
                            val outcome = executeActionDirect(clean, action)
                            if (outcome.isSuccess) {
                                val detail = outcome.getOrNull().orEmpty()
                                if (reply.isBlank()) reply = detail
                                actionSummaries += detail
                            } else {
                                status = "failed"
                                val err = outcome.exceptionOrNull()?.localizedMessage ?: "Action failed"
                                reply = if (reply.isBlank()) err else "$reply\n$err"
                                actionSummaries += err
                            }
                        }
                    }

                    val finalReply = reply.ifBlank { "Done." }
                    repository.addChatMessage(
                        userId = currentUserId,
                        role = "assistant",
                        content = finalReply,
                        languageTag = parsed.languageTag,
                        actionSummary = actionSummaries.joinToString(" • ").take(500),
                        actionStatus = status
                    )
                    speakIfEnabled(finalReply, parsed.languageTag)
                } else {
                    routeToChatGptBrain(clean, langTag)
                }
            } finally {
                _isThinking.value = false
                refreshDeviceDiagnostics()
            }
        }
    }

    private suspend fun handleInfoQuery(rawCommand: String, query: InfoQuery, langTag: String) {
        when (query) {
            is InfoQuery.Weather -> {
                val result = infoClient.fetchWeather(query.city)
                val reply = result.fold(
                    onSuccess = { w ->
                        _weather.value = w
                        if (langTag == "hi") {
                            "${w.locationName} में तापमान ${w.temperatureC}°C है, मौसम: ${w.conditionLabel}, हवा ${w.windSpeedKmh} km/h।"
                        } else {
                            "Weather in ${w.locationName}: ${w.temperatureC}°C, ${w.conditionLabel}, wind ${w.windSpeedKmh} km/h."
                        }
                    },
                    onFailure = { "Unable to fetch weather: ${it.localizedMessage}" }
                )
                repository.addChatMessage(currentUserId, "assistant", reply, langTag, "Weather check", "executed")
                repository.logAction(currentUserId, rawCommand, "WEATHER_UPDATE", query.city, if (result.isSuccess) "executed" else "failed", reply)
                speakIfEnabled(reply, langTag)
            }

            is InfoQuery.News -> {
                val result = infoClient.fetchDailyNews(query.hindi)
                val reply = result.fold(
                    onSuccess = { items ->
                        _news.value = items
                        val lines = items.take(4).mapIndexed { idx, n -> "${idx + 1}. ${n.title}" }
                        if (query.hindi) "आज की मुख्य खबरें:\n${lines.joinToString("\n")}"
                        else "Top daily headlines:\n${lines.joinToString("\n")}"
                    },
                    onFailure = { "Unable to fetch news: ${it.localizedMessage}" }
                )
                repository.addChatMessage(currentUserId, "assistant", reply, langTag, "Daily news brief", "executed")
                repository.logAction(currentUserId, rawCommand, "DAILY_NEWS", "Headlines", if (result.isSuccess) "executed" else "failed", "Fetched headlines")
                speakIfEnabled(reply.lineSequence().take(3).joinToString(". "), langTag)
            }

            is InfoQuery.Search -> {
                val wiki = infoClient.fetchQuickKnowledge(query.query)
                val reply = wiki.fold(
                    onSuccess = { summary -> summary.take(700) },
                    onFailure = {
                        deviceControl.openWebSearch(query.query)
                        if (langTag == "hi") "'${query.query}' के लिए ब्राउज़र सर्च खोला जा रहा है।"
                        else "Opened web search for '${query.query}'."
                    }
                )
                repository.addChatMessage(currentUserId, "assistant", reply, langTag, "Search: ${query.query}", "executed")
                repository.logAction(currentUserId, rawCommand, "WEB_SEARCH", query.query, "executed", reply.take(200))
                speakIfEnabled(reply.take(240), langTag)
            }

            is InfoQuery.Translate -> {
                val cfg = secretConfig.value
                val url = userProfile.value?.backendBaseUrl.orEmpty().ifBlank { cfg.backendBaseUrl }
                val key = cfg.decryptedOpenAiKey()
                if (url.isNotBlank() || key.isNotBlank()) {
                    routeToChatGptBrain(rawCommand, langTag)
                } else {
                    val reply = if (query.targetHindi) {
                        "Full AI translation uses your connected ChatGPT backend (configure in Settings). Quick web translation opened for: ${query.text}"
                    } else {
                        "Full AI translation uses your connected ChatGPT backend (configure in Settings)."
                    }
                    deviceControl.openWebSearch("translate ${query.text} to ${if (query.targetHindi) "hindi" else "english"}")
                    repository.addChatMessage(currentUserId, "assistant", reply, langTag, "Translation link", "executed")
                }
            }
        }
    }

    private suspend fun routeToChatGptBrain(userPrompt: String, langTag: String) {
        val profile = userProfile.value
        val cfg = secretConfig.value
        val history = (chatMessagesState.value as? UiState.Success)?.data.orEmpty()
        val backendUrl = profile?.backendBaseUrl.orEmpty().ifBlank { cfg.backendBaseUrl }
        val model = profile?.openAiModel.orEmpty().ifBlank { cfg.openAiModel }
        val resp = openAiClient.askChatGpt(
            userPrompt = userPrompt,
            history = history,
            languageMode = currentLanguage(),
            approvedPreferences = profile?.approvedPreferencesSummary.orEmpty(),
            telemetry = deviceControl.getDeviceTelemetry(),
            backendBaseUrl = backendUrl,
            backendAuthToken = cfg.decryptedBackendToken(),
            runtimeOpenAiKey = cfg.decryptedOpenAiKey(),
            model = model
        )

        when (resp) {
            is BrainResponse.Success -> {
                val summaries = mutableListOf<String>()
                var status = if (resp.actions.isEmpty()) "none" else "executed"
                for (action in resp.actions) {
                    if (action.requiresConfirmation) {
                        _pendingConfirmation.value = action
                        status = "pending_confirm"
                        summaries += "Pending confirm: ${action.title}"
                    } else {
                        val res = executeActionDirect(userPrompt, action)
                        if (res.isFailure) status = "failed"
                        summaries += res.getOrElse { it.localizedMessage ?: "Failed" }
                    }
                }
                repository.addChatMessage(
                    userId = currentUserId,
                    role = "assistant",
                    content = resp.reply,
                    languageTag = resp.languageTag,
                    actionSummary = summaries.joinToString(" • ").take(500),
                    actionStatus = status
                )
                speakIfEnabled(resp.reply, resp.languageTag)
            }

            is BrainResponse.NotConfigured -> {
                repository.addChatMessage(
                    userId = currentUserId,
                    role = "assistant",
                    content = resp.message,
                    languageTag = langTag,
                    actionSummary = "ChatGPT backend setup needed",
                    actionStatus = "none"
                )
            }

            is BrainResponse.Error -> {
                repository.addChatMessage(
                    userId = currentUserId,
                    role = "assistant",
                    content = resp.message,
                    languageTag = langTag,
                    actionSummary = "ChatGPT request failed",
                    actionStatus = "failed"
                )
            }
        }
    }

    fun confirmPendingAction() {
        val action = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        viewModelScope.launch {
            val res = executeActionDirect(action.title, action)
            val msg = res.getOrElse { it.localizedMessage ?: "Action failed" }
            val status = if (res.isSuccess) "confirmed" else "failed"
            repository.logAction(currentUserId, action.title, action.type.name, action.targetName, status, msg)
            repository.addChatMessage(currentUserId, "assistant", msg, "bilingual", action.title, if (res.isSuccess) "executed" else "failed")
            _events.tryEmit(UiEvent.Snackbar(msg))
            speakIfEnabled(msg, "en")
        }
    }

    fun cancelPendingAction() {
        val action = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        viewModelScope.launch {
            repository.logAction(currentUserId, action.title, action.type.name, action.targetName, "cancelled", "Cancelled by user")
            repository.addChatMessage(currentUserId, "assistant", "Action cancelled: ${action.title}", "en", action.title, "cancelled")
        }
    }

    private suspend fun executeActionDirect(commandText: String, action: PendingAction): Result<String> {
        val result: Result<String> = when (action.type) {
            ActionType.OPEN_APP -> deviceControl.launchApp(action.targetPhoneOrPackage.ifBlank { action.targetName })
            ActionType.CLOSE_APP_GUIDE -> {
                val acc = JarvisAccessibilityService.current()
                if (acc != null && acc.goHome()) Result.success("Returned to Home screen")
                else {
                    deviceControl.openPhoneSettings()
                    Result.success("Opened settings (Android protects apps from force-closing without user consent)")
                }
            }
            ActionType.CALL_CONTACT -> {
                val hasReadContacts = ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                val hasCall = ContextCompat.checkSelfPermission(appContext, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
                val number = if (action.targetPhoneOrPackage.any { it.isDigit() }) {
                    action.targetPhoneOrPackage
                } else if (hasReadContacts) {
                    withContext(Dispatchers.IO) {
                        deviceControl.searchContacts(action.targetName).firstOrNull()?.phoneNumber
                    }.orEmpty()
                } else {
                    _events.tryEmit(UiEvent.RequestRuntimePermission(Manifest.permission.READ_CONTACTS, "Contacts permission helps Jarvis look up '${action.targetName}' by name."))
                    ""
                }
                if (number.isBlank()) Result.failure(IllegalStateException("Contact '${action.targetName}' not found or Contacts permission required."))
                else deviceControl.placeCall(number, hasCall)
            }
            ActionType.SEND_SMS -> {
                val hasReadContacts = ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                val hasSms = ContextCompat.checkSelfPermission(appContext, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
                val number = if (action.targetPhoneOrPackage.any { it.isDigit() }) {
                    action.targetPhoneOrPackage
                } else if (hasReadContacts) {
                    withContext(Dispatchers.IO) {
                        deviceControl.searchContacts(action.targetName).firstOrNull()?.phoneNumber
                    }.orEmpty()
                } else ""
                deviceControl.sendSms(number, action.payloadText, hasSms)
            }
            ActionType.WHATSAPP_DRAFT -> {
                val hasReadContacts = ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                val number = if (action.targetPhoneOrPackage.any { it.isDigit() }) {
                    action.targetPhoneOrPackage
                } else if (hasReadContacts && action.targetName.isNotBlank()) {
                    withContext(Dispatchers.IO) {
                        deviceControl.searchContacts(action.targetName).firstOrNull()?.phoneNumber
                    }.orEmpty()
                } else ""
                deviceControl.openWhatsAppDraft(number, action.payloadText)
            }
            ActionType.OPEN_FACEBOOK -> deviceControl.openFacebook()
            ActionType.FACEBOOK_POST -> deviceControl.shareToFacebook(action.payloadText, action.attachmentUri ?: _selectedSocialPhotoUri.value)
            ActionType.SET_ALARM -> deviceControl.setSystemAlarm(action.hour, action.minute, action.payloadText)
            ActionType.SET_TIMER -> deviceControl.setSystemTimer(action.seconds, action.payloadText)
            ActionType.CREATE_TASK -> {
                val r = repository.addTask(
                    userId = currentUserId,
                    title = action.payloadText.ifBlank { action.description },
                    category = action.taskCategory
                )
                if (r.isSuccess) Result.success("Saved to ${action.taskCategory.label}: ${action.payloadText}")
                else Result.failure(r.exceptionOrNull() ?: IllegalStateException("Failed to save task"))
            }
            ActionType.ADD_CALENDAR_EVENT -> deviceControl.addCalendarEvent(action.payloadText, action.description)
            ActionType.ADJUST_VOLUME -> {
                val pct = deviceControl.adjustMediaVolume(action.volumeDelta)
                refreshDeviceDiagnostics()
                Result.success("Media volume set to $pct%")
            }
            ActionType.OPEN_WIFI_SETTINGS -> deviceControl.openWifiSettingsPanel()
            ActionType.OPEN_BLUETOOTH_SETTINGS -> deviceControl.openBluetoothSettings()
            ActionType.OPEN_PHONE_SETTINGS -> deviceControl.openPhoneSettings()
            ActionType.OPEN_BATTERY_OPTIMIZATION -> deviceControl.openBatteryOptimizationSettings()
            ActionType.CAPTURE_PHOTO -> {
                val hasCam = ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                if (!hasCam) {
                    _events.tryEmit(UiEvent.RequestRuntimePermission(Manifest.permission.CAMERA, "Camera permission is needed to capture a photo."))
                    Result.failure(IllegalStateException("Camera permission requested."))
                } else {
                    _events.tryEmit(UiEvent.RequestCameraCapture)
                    Result.success("Launching camera...")
                }
            }
            ActionType.PICK_PHOTO -> {
                _events.tryEmit(UiEvent.RequestPhotoPicker)
                Result.success("Opening Android Photo Picker...")
            }
            ActionType.READ_NOTIFICATIONS -> {
                if (!JarvisNotificationListenerService.isEnabled(appContext)) {
                    Result.failure(IllegalStateException("Notification Access is not enabled yet. Enable it in Settings > Device Permissions."))
                } else {
                    val items = notifications.value
                    if (items.isEmpty()) Result.success("You have no recent unread notifications.")
                    else {
                        val summary = items.take(4).joinToString(". ") { "${it.appName}: ${it.title} ${it.body}".trim() }
                        Result.success("Recent notifications: $summary")
                    }
                }
            }
            ActionType.BATTERY_STORAGE_STATUS -> {
                val t = deviceControl.getDeviceTelemetry()
                _telemetry.value = t
                val state = if (t.charging) "charging" else "on battery"
                Result.success("Battery is ${t.batteryPercent}% ($state, ${t.batteryTempC}°C). Storage: ${t.freeStorageGb} GB free of ${t.totalStorageGb} GB. RAM free: ${t.availableRamGb} GB.")
            }
            ActionType.WEATHER_UPDATE -> {
                val w = infoClient.fetchWeather(action.targetName.ifBlank { "New Delhi" })
                w.map { "${it.locationName}: ${it.temperatureC}°C, ${it.conditionLabel}" }
            }
            ActionType.DAILY_NEWS -> {
                val n = infoClient.fetchDailyNews(currentLanguage() == LanguageMode.HINDI)
                n.map { list -> list.take(3).joinToString(" | ") { it.title } }
            }
            ActionType.WEB_SEARCH -> deviceControl.openWebSearch(action.payloadText.ifBlank { action.targetName })
            ActionType.TRANSLATE -> deviceControl.openWebSearch("translate ${action.payloadText}")
            ActionType.CALCULATE -> Result.success(action.description)
            ActionType.NAVIGATION_BACK -> {
                val acc = JarvisAccessibilityService.current()
                if (acc != null && acc.goBack()) Result.success("Navigated back")
                else Result.failure(IllegalStateException("Enable Jarvis Navigation Accessibility Service in Settings first."))
            }
            ActionType.NAVIGATION_HOME -> {
                val acc = JarvisAccessibilityService.current()
                if (acc != null && acc.goHome()) Result.success("Returned Home")
                else Result.failure(IllegalStateException("Enable Jarvis Navigation Accessibility Service in Settings first."))
            }
            ActionType.NAVIGATION_RECENTS -> {
                val acc = JarvisAccessibilityService.current()
                if (acc != null && acc.openRecents()) Result.success("Opened Recents")
                else Result.failure(IllegalStateException("Enable Jarvis Navigation Accessibility Service in Settings first."))
            }
            ActionType.CHAT_ONLY -> Result.success(action.description)
        }

        val outcomeMsg = result.getOrElse { it.localizedMessage ?: "Failed" }
        repository.logAction(
            userId = currentUserId,
            commandText = commandText,
            actionType = action.type.name,
            targetSummary = action.targetName.ifBlank { action.title },
            status = if (result.isSuccess) "executed" else "failed",
            detailMessage = outcomeMsg
        )
        return result
    }

    private fun speakIfEnabled(text: String, languageTag: String) {
        if (userProfile.value?.voiceEnabled != false) {
            voiceEngine.speak(text, languageTag)
        }
    }

    fun onPhotoPicked(uri: Uri?) {
        _selectedSocialPhotoUri.value = uri
        if (uri != null) {
            _events.tryEmit(UiEvent.Snackbar("Photo attached for social post or preview."))
        }
    }

    fun onPhotoCaptured(uri: Uri?) {
        _lastCapturedPhotoUri.value = uri
        _selectedSocialPhotoUri.value = uri
        if (uri != null) {
            _events.tryEmit(UiEvent.Snackbar("Photo captured and ready."))
        }
    }

    fun queueFacebookPostConfirmation(caption: String) {
        _pendingConfirmation.value = PendingAction(
            id = "fb_${System.currentTimeMillis()}",
            type = ActionType.FACEBOOK_POST,
            title = "Confirm Facebook Post",
            description = "Caption: \"${caption.ifBlank { "(Photo only)" }}\"${if (_selectedSocialPhotoUri.value != null) " + Attached Photo" else ""}",
            requiresConfirmation = true,
            targetName = "Facebook",
            payloadText = caption,
            attachmentUri = _selectedSocialPhotoUri.value
        )
    }

    fun queueCallConfirmation(contact: ContactMatch) {
        _pendingConfirmation.value = PendingAction(
            id = "call_${System.currentTimeMillis()}",
            type = ActionType.CALL_CONTACT,
            title = "Confirm Phone Call",
            description = "Call ${contact.name} (${contact.phoneNumber})?",
            requiresConfirmation = true,
            targetName = contact.name,
            targetPhoneOrPackage = contact.phoneNumber
        )
    }

    fun queueSmsConfirmation(contact: ContactMatch, message: String) {
        _pendingConfirmation.value = PendingAction(
            id = "sms_${System.currentTimeMillis()}",
            type = ActionType.SEND_SMS,
            title = "Confirm SMS",
            description = "Send SMS to ${contact.name} (${contact.phoneNumber}): \"$message\"",
            requiresConfirmation = true,
            targetName = contact.name,
            targetPhoneOrPackage = contact.phoneNumber,
            payloadText = message
        )
    }

    fun queueWhatsAppConfirmation(contactName: String, phone: String, message: String) {
        _pendingConfirmation.value = PendingAction(
            id = "wa_${System.currentTimeMillis()}",
            type = ActionType.WHATSAPP_DRAFT,
            title = "Confirm WhatsApp Draft",
            description = "Open WhatsApp for ${contactName.ifBlank { "recipient" }}: \"$message\"",
            requiresConfirmation = true,
            targetName = contactName,
            targetPhoneOrPackage = phone,
            payloadText = message
        )
    }

    fun createTaskItem(
        title: String,
        notes: String,
        category: TaskCategory,
        dueDateLabel: String,
        dueTimeLabel: String,
        priority: TaskPriority
    ) {
        viewModelScope.launch {
            val res = repository.addTask(currentUserId, title, notes, category, dueDateLabel, dueTimeLabel, priority)
            if (res.isFailure) {
                _events.tryEmit(UiEvent.Snackbar(res.exceptionOrNull()?.localizedMessage ?: "Failed to save item"))
            } else {
                _events.tryEmit(UiEvent.Snackbar("${category.label} saved."))
            }
        }
    }

    fun toggleTaskCompleted(task: AssistantTask) {
        viewModelScope.launch {
            repository.toggleTaskCompleted(currentUserId, task.id, !task.completed)
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            repository.deleteTask(currentUserId, taskId)
        }
    }

    fun setMediaVolume(percent: Int) {
        deviceControl.setMediaVolumePercent(percent)
        refreshDeviceDiagnostics()
    }

    fun launchInstalledApp(app: InstalledAppInfo) {
        val res = deviceControl.launchApp(app.packageName)
        if (res.isFailure) _events.tryEmit(UiEvent.Snackbar(res.exceptionOrNull()?.localizedMessage ?: "Cannot open app"))
    }

    fun openAppInfoForClose(app: InstalledAppInfo) {
        deviceControl.openAppInfoSettings(app.packageName)
    }

    fun setSystemAlarmDirect(hour: Int, minute: Int, label: String) {
        val res = deviceControl.setSystemAlarm(hour, minute, label)
        _events.tryEmit(UiEvent.Snackbar(res.getOrElse { it.localizedMessage ?: "Alarm error" }))
    }

    fun openCalendarEvent(title: String, notes: String) {
        deviceControl.addCalendarEvent(title, notes)
    }

    fun openWifiSettings() { deviceControl.openWifiSettingsPanel() }
    fun openBluetoothSettings() { deviceControl.openBluetoothSettings() }
    fun openPhoneSettings() { deviceControl.openPhoneSettings() }
    fun openBatteryOptimizationSettings() { deviceControl.openBatteryOptimizationSettings() }
    fun openAccessibilitySettings() { deviceControl.openAccessibilitySettings() }
    fun openNotificationListenerSettings() { deviceControl.openNotificationListenerSettings() }
    fun openFacebookDirect() { deviceControl.openFacebook() }
    fun isBatteryOptimizationIgnored(): Boolean = deviceControl.isIgnoringBatteryOptimizations()
    fun isAccessibilityEnabled(): Boolean = JarvisAccessibilityService.isEnabled(appContext)
    fun isNotificationListenerEnabled(): Boolean = JarvisNotificationListenerService.isEnabled(appContext)

    fun saveLanguageMode(mode: LanguageMode) {
        viewModelScope.launch {
            repository.updateUserPreferences(currentUserId, preferredLanguage = mode)
        }
    }

    fun saveVoiceEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (!enabled) voiceEngine.stopSpeaking()
            repository.updateUserPreferences(currentUserId, voiceEnabled = enabled)
        }
    }

    fun saveVoiceTuning(pitch: Float, rate: Float) {
        viewModelScope.launch {
            secretStore.saveVoiceTuning(pitch, rate)
            voiceEngine.updateVoiceTuning(pitch, rate)
        }
    }

    fun saveApprovedPreferences(summary: String) {
        viewModelScope.launch {
            val res = repository.updateUserPreferences(currentUserId, approvedPreferencesSummary = summary)
            _events.tryEmit(
                UiEvent.Snackbar(
                    if (res.isSuccess) "Approved preferences saved." else "Failed to update preferences."
                )
            )
        }
    }

    fun saveAiBackendSettings(
        backendUrl: String,
        model: String,
        runtimeOpenAiKey: String?,
        backendToken: String?
    ) {
        viewModelScope.launch {
            secretStore.saveBackendConfig(backendUrl, model, runtimeOpenAiKey, backendToken)
            repository.updateUserPreferences(
                userId = currentUserId,
                backendBaseUrl = backendUrl,
                openAiModel = model
            )
            _events.tryEmit(UiEvent.Snackbar("ChatGPT backend configuration updated."))
        }
    }

    fun updateConsentFlags(accessibilityConsent: Boolean? = null, notificationConsent: Boolean? = null) {
        viewModelScope.launch {
            repository.updateUserPreferences(
                userId = currentUserId,
                accessibilityConsentGranted = accessibilityConsent,
                notificationListenerConsentGranted = notificationConsent
            )
        }
    }

    fun createCameraCaptureUri(): Uri = deviceControl.createTempCameraUri()

    override fun onCleared() {
        super.onCleared()
        voiceEngine.release()
    }
}
