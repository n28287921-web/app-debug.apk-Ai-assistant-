package com.example.data.remote

import com.example.data.model.ActionType
import com.example.data.model.ChatMessage
import com.example.data.model.DeviceTelemetry
import com.example.data.model.LanguageMode
import com.example.data.model.PendingAction
import com.example.data.model.TaskCategory
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

sealed interface BrainResponse {
    data class Success(
        val reply: String,
        val languageTag: String,
        val actions: List<PendingAction>
    ) : BrainResponse

    data class NotConfigured(val message: String) : BrainResponse
    data class Error(val message: String) : BrainResponse
}

class OpenAiBrainClient {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun askChatGpt(
        userPrompt: String,
        history: List<ChatMessage>,
        languageMode: LanguageMode,
        approvedPreferences: String,
        telemetry: DeviceTelemetry,
        backendBaseUrl: String,
        backendAuthToken: String,
        runtimeOpenAiKey: String,
        model: String
    ): BrainResponse = withContext(Dispatchers.IO) {
        val cleanBackendUrl = backendBaseUrl.trim().trimEnd('/')
        val cleanKey = runtimeOpenAiKey.trim()

        if (cleanBackendUrl.isBlank() && cleanKey.isBlank()) {
            return@withContext BrainResponse.NotConfigured(
                if (languageMode == LanguageMode.HINDI) {
                    "ChatGPT क्लाउड ब्रेन अभी कनेक्ट नहीं है। Settings में जाकर अपना Secure Backend URL (या टेस्टिंग API कुंजी) सेट करें।"
                } else {
                    "ChatGPT cloud brain is not connected yet. Configure your Secure Backend Proxy URL (or optional test API key) in Settings. On-device phone commands, calculator, weather, news, and alarms remain active!"
                }
            )
        }

        val systemPrompt = buildSystemPrompt(languageMode, approvedPreferences, telemetry)
        val messagesArray = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            history.takeLast(10).forEach { msg ->
                val role = if (msg.role == "assistant") "assistant" else "user"
                put(JSONObject().put("role", role).put("content", msg.content.take(2000)))
            }
            put(JSONObject().put("role", "user").put("content", userPrompt))
        }

        try {
            if (cleanBackendUrl.isNotBlank()) {
                val firebaseIdToken = try {
                    Firebase.auth.currentUser?.getIdToken(false)?.await()?.token.orEmpty()
                } catch (_: Exception) {
                    ""
                }
                val endpoint = if (cleanBackendUrl.endsWith("/api/jarvis/chat")) {
                    cleanBackendUrl
                } else {
                    "$cleanBackendUrl/api/jarvis/chat"
                }
                val bodyJson = JSONObject().apply {
                    put("model", model.ifBlank { "gpt-4o-mini" })
                    put("language", languageMode.firestoreValue)
                    put("messages", messagesArray)
                    put("approvedPreferences", approvedPreferences)
                    put("deviceContext", JSONObject().apply {
                        put("batteryPercent", telemetry.batteryPercent)
                        put("charging", telemetry.charging)
                        put("freeStorageGb", telemetry.freeStorageGb)
                        put("deviceModel", telemetry.deviceModel)
                    })
                }
                val reqBuilder = Request.Builder()
                    .url(endpoint)
                    .post(bodyJson.toString().toRequestBody(jsonMediaType))
                    .header("Content-Type", "application/json")
                val tokenToUse = backendAuthToken.ifBlank { firebaseIdToken }
                if (tokenToUse.isNotBlank()) {
                    reqBuilder.header("Authorization", "Bearer $tokenToUse")
                }
                executeAndParse(reqBuilder.build())
            } else {
                val bodyJson = JSONObject().apply {
                    put("model", model.ifBlank { "gpt-4o-mini" })
                    put("temperature", 0.5)
                    put("response_format", JSONObject().put("type", "json_object"))
                    put("messages", messagesArray)
                }
                val req = Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .post(bodyJson.toString().toRequestBody(jsonMediaType))
                    .header("Authorization", "Bearer $cleanKey")
                    .header("Content-Type", "application/json")
                    .build()
                executeAndParse(req)
            }
        } catch (e: Exception) {
            BrainResponse.Error("ChatGPT connection error: ${e.localizedMessage ?: "Network failure"}")
        }
    }

    private fun executeAndParse(request: Request): BrainResponse {
        httpClient.newCall(request).execute().use { resp ->
            val rawBody = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val errMsg = try {
                    JSONObject(rawBody).optJSONObject("error")?.optString("message")
                        ?.takeIf { it.isNotBlank() }
                        ?: JSONObject(rawBody).optString("error")
                            .takeIf { it.isNotBlank() }
                        ?: "HTTP ${resp.code}"
                } catch (_: Exception) {
                    "HTTP ${resp.code}"
                }
                return BrainResponse.Error("ChatGPT API error: $errMsg")
            }

            val root = JSONObject(rawBody)
            // Support either direct OpenAI chat completion shape or backend proxy { reply, language, actions }
            if (root.has("reply")) {
                val reply = root.optString("reply", "").trim()
                val lang = root.optString("language", "bilingual")
                val actions = parseActions(root.optJSONArray("actions"))
                return BrainResponse.Success(
                    reply = reply.ifBlank { "Ready." },
                    languageTag = lang,
                    actions = actions
                )
            }

            val content = root.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
                .trim()

            if (content.isBlank()) {
                return BrainResponse.Error("Empty response from ChatGPT.")
            }

            return try {
                val parsed = JSONObject(content)
                val reply = parsed.optString("reply", content).trim()
                val lang = parsed.optString("language", "bilingual")
                val actions = parseActions(parsed.optJSONArray("actions"))
                BrainResponse.Success(reply = reply, languageTag = lang, actions = actions)
            } catch (_: Exception) {
                BrainResponse.Success(reply = content, languageTag = "bilingual", actions = emptyList())
            }
        }
    }

    private fun parseActions(array: JSONArray?): List<PendingAction> {
        if (array == null) return emptyList()
        val list = mutableListOf<PendingAction>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val rawType = obj.optString("type", "").uppercase()
            val actionType = ActionType.entries.firstOrNull { it.name == rawType } ?: continue
            val target = obj.optString("target", "")
            val text = obj.optString("text", "")
            val requiresConfirm = actionType in setOf(
                ActionType.CALL_CONTACT,
                ActionType.SEND_SMS,
                ActionType.WHATSAPP_DRAFT,
                ActionType.FACEBOOK_POST
            )
            list += PendingAction(
                id = UUID.randomUUID().toString(),
                type = actionType,
                title = obj.optString("title", actionType.name.replace('_', ' ')),
                description = obj.optString("description", text.ifBlank { target }),
                requiresConfirmation = requiresConfirm,
                targetName = target,
                targetPhoneOrPackage = obj.optString("packageOrNumber", target),
                payloadText = text,
                hour = obj.optInt("hour", 7).coerceIn(0, 23),
                minute = obj.optInt("minute", 0).coerceIn(0, 59),
                seconds = obj.optInt("seconds", 60).coerceAtLeast(1),
                taskCategory = TaskCategory.fromRaw(obj.optString("category", "task"))
            )
        }
        return list
    }

    private fun buildSystemPrompt(
        languageMode: LanguageMode,
        approvedPreferences: String,
        telemetry: DeviceTelemetry
    ): String {
        val langInstruction = when (languageMode) {
            LanguageMode.HINDI -> "Respond naturally in warm, respectful Hindi (Devanagari script) with a helpful female AI assistant persona."
            LanguageMode.ENGLISH -> "Respond naturally in crisp, intelligent English with a helpful female AI assistant persona."
            LanguageMode.BILINGUAL -> "Understand both Hindi (Devanagari/Hinglish) and English seamlessly. Reply in the same language the user used."
        }
        return """
            You are JARVIS VIP, a futuristic, intelligent, polite female personal AI assistant on Android.
            $langInstruction
            User-approved preferences: ${approvedPreferences.ifBlank { "None set yet." }}
            Live device status: Battery ${telemetry.batteryPercent}% (charging=${telemetry.charging}), Free Storage ${telemetry.freeStorageGb} GB, Model ${telemetry.deviceModel}.
            Return ONLY valid JSON with this schema:
            {
              "reply": "natural spoken response for the user",
              "language": "en" | "hi" | "bilingual",
              "actions": [
                {
                  "type": "OPEN_APP|CALL_CONTACT|SEND_SMS|WHATSAPP_DRAFT|FACEBOOK_POST|OPEN_FACEBOOK|SET_ALARM|SET_TIMER|CREATE_TASK|ADD_CALENDAR_EVENT|ADJUST_VOLUME|OPEN_WIFI_SETTINGS|OPEN_BLUETOOTH_SETTINGS|OPEN_PHONE_SETTINGS|CAPTURE_PHOTO|PICK_PHOTO|READ_NOTIFICATIONS|WEATHER_UPDATE|DAILY_NEWS|WEB_SEARCH",
                  "title": "Short action title",
                  "description": "What will happen",
                  "target": "App name, contact name, or city",
                  "packageOrNumber": "Optional package or phone number",
                  "text": "Message, caption, task title, or search query",
                  "hour": 7,
                  "minute": 0,
                  "seconds": 60,
                  "category": "task|reminder|schedule|birthday"
                }
              ]
            }
            If no device action is required, return "actions": []. Never claim an action succeeded unless executed by the app.
        """.trimIndent()
    }
}
