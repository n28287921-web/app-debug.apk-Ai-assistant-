package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.jarvisPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "jarvis_vip_secure_prefs")

data class LocalSecretConfig(
    val backendBaseUrl: String = "",
    val encryptedRuntimeOpenAiKey: String = "",
    val encryptedBackendAuthToken: String = "",
    val openAiModel: String = "gpt-4o-mini",
    val ttsPitch: Float = 1.08f,
    val ttsRate: Float = 1.0f,
    val wakeWordServiceEnabled: Boolean = false
) {
    fun decryptedOpenAiKey(): String = KeystoreCrypto.decrypt(encryptedRuntimeOpenAiKey)
    fun decryptedBackendToken(): String = KeystoreCrypto.decrypt(encryptedBackendAuthToken)
}

class SecurePreferencesStore(private val context: Context) {
    private object Keys {
        val BACKEND_BASE_URL = stringPreferencesKey("backend_base_url")
        val ENCRYPTED_OPENAI_KEY = stringPreferencesKey("encrypted_openai_key")
        val ENCRYPTED_BACKEND_TOKEN = stringPreferencesKey("encrypted_backend_token")
        val OPENAI_MODEL = stringPreferencesKey("openai_model")
        val TTS_PITCH = floatPreferencesKey("tts_pitch")
        val TTS_RATE = floatPreferencesKey("tts_rate")
        val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
    }

    val configFlow: Flow<LocalSecretConfig> = context.jarvisPrefsDataStore.data.map { prefs ->
        LocalSecretConfig(
            backendBaseUrl = prefs[Keys.BACKEND_BASE_URL].orEmpty(),
            encryptedRuntimeOpenAiKey = prefs[Keys.ENCRYPTED_OPENAI_KEY].orEmpty(),
            encryptedBackendAuthToken = prefs[Keys.ENCRYPTED_BACKEND_TOKEN].orEmpty(),
            openAiModel = prefs[Keys.OPENAI_MODEL] ?: "gpt-4o-mini",
            ttsPitch = prefs[Keys.TTS_PITCH] ?: 1.08f,
            ttsRate = prefs[Keys.TTS_RATE] ?: 1.0f,
            wakeWordServiceEnabled = prefs[Keys.WAKE_WORD_ENABLED] ?: false
        )
    }

    suspend fun saveBackendConfig(
        backendUrl: String,
        model: String,
        runtimeOpenAiKey: String?,
        backendToken: String?
    ) {
        context.jarvisPrefsDataStore.edit { prefs ->
            prefs[Keys.BACKEND_BASE_URL] = backendUrl.trim().take(300)
            prefs[Keys.OPENAI_MODEL] = model.trim().ifBlank { "gpt-4o-mini" }.take(60)
            if (runtimeOpenAiKey != null) {
                prefs[Keys.ENCRYPTED_OPENAI_KEY] =
                    if (runtimeOpenAiKey.isBlank()) "" else KeystoreCrypto.encrypt(runtimeOpenAiKey.trim())
            }
            if (backendToken != null) {
                prefs[Keys.ENCRYPTED_BACKEND_TOKEN] =
                    if (backendToken.isBlank()) "" else KeystoreCrypto.encrypt(backendToken.trim())
            }
        }
    }

    suspend fun saveVoiceTuning(pitch: Float, rate: Float) {
        context.jarvisPrefsDataStore.edit { prefs ->
            prefs[Keys.TTS_PITCH] = pitch.coerceIn(0.75f, 1.4f)
            prefs[Keys.TTS_RATE] = rate.coerceIn(0.75f, 1.4f)
        }
    }

    suspend fun setWakeWordServiceEnabled(enabled: Boolean) {
        context.jarvisPrefsDataStore.edit { prefs ->
            prefs[Keys.WAKE_WORD_ENABLED] = enabled
        }
    }
}
