package com.example.domain

import com.example.data.model.ActionType
import com.example.data.model.PendingAction
import com.example.data.model.TaskCategory
import java.util.UUID

data class LocalCommandParseResult(
    val handledLocally: Boolean,
    val replyText: String = "",
    val languageTag: String = "en",
    val actions: List<PendingAction> = emptyList(),
    val infoQuery: InfoQuery? = null
)

sealed interface InfoQuery {
    data class Weather(val city: String) : InfoQuery
    data class News(val hindi: Boolean) : InfoQuery
    data class Search(val query: String) : InfoQuery
    data class Translate(val text: String, val targetHindi: Boolean) : InfoQuery
}

object JarvisCommandEngine {
    private val hindiRange = Regex("[\\u0900-\\u097F]")
    private val hinglishKeywords = listOf(
        "karo", "kholo", "lagao", "bhejo", "batao", "padho", "kitni", "kaisa", "mausam",
        "khabar", "awaz", "badhao", "ghatao", "chalu", "band", "yaad", "janamdin", "mujhe"
    )

    fun detectLanguageTag(text: String): String {
        if (hindiRange.containsMatchIn(text)) return "hi"
        val lower = text.lowercase()
        if (hinglishKeywords.any { lower.contains(it) }) return "hi"
        return "en"
    }

    fun stripWakeWord(rawText: String): String {
        return rawText
            .replace(Regex("^(hey\\s+jarvis|ok\\s+jarvis|jarvis|हे\\s+जार्विस|जार्विस)[,:\\s]*", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    fun parseCommand(rawInput: String): LocalCommandParseResult {
        val cleaned = stripWakeWord(rawInput).ifBlank { rawInput.trim() }
        val lower = cleaned.lowercase()
        val lang = detectLanguageTag(cleaned)
        val isHindi = lang == "hi"

        // 1. Math Calculation
        val mathMatch = Regex("^(?:calculate|calc|what is|solve|हिसाब|गणना)\\s+([0-9.+\\-*/%^()\\s×÷,]+)$", RegexOption.IGNORE_CASE).find(cleaned)
            ?: Regex("^([0-9]+(?:\\.[0-9]+)?\\s*[+\\-*/%^×÷]\\s*[0-9.+\\-*/%^()\\s×÷]+)$").find(cleaned)
        if (mathMatch != null) {
            return try {
                val expr = mathMatch.groupValues[1]
                val res = MathEvaluator.evaluate(expr)
                val formatted = if (res % 1.0 == 0.0) res.toLong().toString() else String.format("%.4f", res).trimEnd('0').trimEnd('.')
                LocalCommandParseResult(
                    handledLocally = true,
                    replyText = if (isHindi) "उत्तर है $formatted।" else "The result is $formatted.",
                    languageTag = lang,
                    actions = listOf(
                        PendingAction(
                            id = uid(),
                            type = ActionType.CALCULATE,
                            title = "Calculation",
                            description = "$expr = $formatted",
                            requiresConfirmation = false,
                            payloadText = formatted
                        )
                    )
                )
            } catch (_: Exception) {
                LocalCommandParseResult(handledLocally = false, languageTag = lang)
            }
        }

        // 2. Battery & Storage Telemetry
        if (lower.contains("battery") || lower.contains("storage") || lower.contains("ram") ||
            lower.contains("बैटरी") || lower.contains("स्टोरेज")
        ) {
            return LocalCommandParseResult(
                handledLocally = true,
                languageTag = lang,
                actions = listOf(
                    PendingAction(
                        id = uid(),
                        type = ActionType.BATTERY_STORAGE_STATUS,
                        title = "Device Diagnostics",
                        description = "Inspect battery, RAM, and internal storage",
                        requiresConfirmation = false
                    )
                )
            )
        }

        // 3. Weather Updates
        if (lower.contains("weather") || lower.contains("mausam") || lower.contains("मौसम")) {
            val city = Regex("(?:weather|mausam|मौसम)(?:\\s+(?:in|at|of|में|का))?\\s+([a-zA-Z\\s]+)", RegexOption.IGNORE_CASE)
                .find(cleaned)?.groupValues?.getOrNull(1)?.trim().orEmpty().ifBlank { "New Delhi" }
            return LocalCommandParseResult(
                handledLocally = true,
                languageTag = lang,
                infoQuery = InfoQuery.Weather(city)
            )
        }

        // 4. Daily News
        if (lower.contains("news") || lower.contains("headlines") || lower.contains("khabar") || lower.contains("खबर") || lower.contains("समाचार")) {
            return LocalCommandParseResult(
                handledLocally = true,
                languageTag = lang,
                infoQuery = InfoQuery.News(hindi = isHindi)
            )
        }

        // 5. Read Notifications
        if (lower.contains("notification") || lower.contains("नोटिफिकेशन") || lower.contains("सूचना")) {
            return LocalCommandParseResult(
                handledLocally = true,
                languageTag = lang,
                actions = listOf(
                    PendingAction(
                        id = uid(),
                        type = ActionType.READ_NOTIFICATIONS,
                        title = "Read Notifications",
                        description = "Read recent notifications aloud",
                        requiresConfirmation = false
                    )
                )
            )
        }

        // 6. Phone Call (Confirmation Required)
        val callMatch = Regex("^(?:call|dial|phone|फ़ोन\\s+लगाओ|कॉल\\s+करो)\\s+(.+)$", RegexOption.IGNORE_CASE).find(cleaned)
            ?: Regex("^(.+?)\\s+(?:ko\\s+call\\s+karo|को\\s+कॉल\\s+करो)$", RegexOption.IGNORE_CASE).find(cleaned)
        if (callMatch != null) {
            val contact = callMatch.groupValues[1].trim()
            return LocalCommandParseResult(
                handledLocally = true,
                replyText = if (isHindi) "$contact को कॉल करने की पुष्टि करें।" else "Ready to call $contact. Please confirm to proceed.",
                languageTag = lang,
                actions = listOf(
                    PendingAction(
                        id = uid(),
                        type = ActionType.CALL_CONTACT,
                        title = "Call $contact",
                        description = "Look up '$contact' in contacts and place call",
                        requiresConfirmation = true,
                        targetName = contact
                    )
                )
            )
        }

        // 7. WhatsApp Message Draft (Confirmation Required)
        if (lower.startsWith("whatsapp") || lower.contains("whatsapp message") || lower.contains("व्हाट्सएप")) {
            val waMatch = Regex("whatsapp\\s+(?:to\\s+)?([^:]+?)(?:\\s+(?:saying|message|text|:)\\s+(.+))?$", RegexOption.IGNORE_CASE).find(cleaned)
            val target = waMatch?.groupValues?.getOrNull(1)?.trim().orEmpty()
            val message = waMatch?.groupValues?.getOrNull(2)?.trim().orEmpty().ifBlank { "Hello from Jarvis VIP" }
            return LocalCommandParseResult(
                handledLocally = true,
                replyText = if (isHindi) "WhatsApp संदेश ड्राफ्ट तैयार है। भेजने से पहले पुष्टि करें।" else "WhatsApp draft prepared. Please review and confirm.",
                languageTag = lang,
                actions = listOf(
                    PendingAction(
                        id = uid(),
                        type = ActionType.WHATSAPP_DRAFT,
                        title = "WhatsApp Draft",
                        description = "Draft WhatsApp message${if (target.isNotBlank()) " for $target" else ""}: \"$message\"",
                        requiresConfirmation = true,
                        targetName = target,
                        payloadText = message
                    )
                )
            )
        }

        // 8. SMS Message (Confirmation Required)
        if (lower.startsWith("send sms") || lower.startsWith("sms ") || lower.startsWith("text ") || lower.contains("संदेश भेजो")) {
            val smsMatch = Regex("(?:send\\s+sms|sms|text)\\s+(?:to\\s+)?([^:]+?)(?:\\s+(?:saying|that|:)\\s+(.+))?$", RegexOption.IGNORE_CASE).find(cleaned)
            val target = smsMatch?.groupValues?.getOrNull(1)?.trim().orEmpty()
            val message = smsMatch?.groupValues?.getOrNull(2)?.trim().orEmpty()
            return LocalCommandParseResult(
                handledLocally = true,
                replyText = if (isHindi) "SMS भेजने के लिए कृपया पुष्टि करें।" else "SMS prepared for $target. Please confirm before sending.",
                languageTag = lang,
                actions = listOf(
                    PendingAction(
                        id = uid(),
                        type = ActionType.SEND_SMS,
                        title = "Send SMS to ${target.ifBlank { "Contact" }}",
                        description = "Message: ${message.ifBlank { "(Enter message in SMS app)" }}",
                        requiresConfirmation = true,
                        targetName = target,
                        payloadText = message
                    )
                )
            )
        }

        // 9. Facebook Social Media
        if (lower.contains("facebook") || lower.contains("fb ") || lower.contains("फेसबुक")) {
            val isPostPrep = lower.contains("post") || lower.contains("caption") || lower.contains("share") || lower.contains("upload") || lower.contains("पोस्ट")
            val caption = cleaned
                .replace(Regex(".*?(?:saying|caption|about|post)\\s*", RegexOption.IGNORE_CASE), "")
                .takeIf { it != cleaned && it.isNotBlank() }.orEmpty()
            return if (isPostPrep) {
                LocalCommandParseResult(
                    handledLocally = true,
                    replyText = if (isHindi) "Facebook पोस्ट तैयार है। प्रकाशित करने से पहले पुष्टि करें।" else "Facebook post draft is ready for your confirmation.",
                    languageTag = lang,
                    actions = listOf(
                        PendingAction(
                            id = uid(),
                            type = ActionType.FACEBOOK_POST,
                            title = "Prepare Facebook Post",
                            description = if (caption.isNotBlank()) "Caption: \"$caption\"" else "Open Facebook Social Studio to pick a photo and add a caption",
                            requiresConfirmation = true,
                            payloadText = caption
                        )
                    )
                )
            } else {
                LocalCommandParseResult(
                    handledLocally = true,
                    replyText = if (isHindi) "Facebook खोला जा रहा है।" else "Opening Facebook.",
                    languageTag = lang,
                    actions = listOf(
                        PendingAction(
                            id = uid(),
                            type = ActionType.OPEN_FACEBOOK,
                            title = "Open Facebook",
                            description = "Launch Facebook app or mobile web",
                            requiresConfirmation = false
                        )
                    )
                )
            }
        }

        // 10. Set Alarm or Timer
        if (lower.contains("alarm") || lower.contains("अलार्म")) {
            val timeMatch = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|सुबह|शाम)?", RegexOption.IGNORE_CASE).find(cleaned)
            var hour = timeMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 7
            val minute = timeMatch?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
            val amPm = timeMatch?.groupValues?.getOrNull(3)?.lowercase().orEmpty()
            if ((amPm == "pm" || amPm == "शाम") && hour in 1..11) hour += 12
            if ((amPm == "am" || amPm == "सुबह") && hour == 12) hour = 0
            val formattedTime = String.format("%02d:%02d", hour.coerceIn(0, 23), minute.coerceIn(0, 59))
            return LocalCommandParseResult(
                handledLocally = true,
                replyText = if (isHindi) "$formattedTime बजे का अलार्म सेट किया जा रहा है।" else "Setting an alarm for $formattedTime.",
                languageTag = lang,
                actions = listOf(
                    PendingAction(
                        id = uid(),
                        type = ActionType.SET_ALARM,
                        title = "Set Alarm ($formattedTime)",
                        description = "Create system alarm for $formattedTime",
                        requiresConfirmation = false,
                        payloadText = "Jarvis VIP Alarm",
                        hour = hour.coerceIn(0, 23),
                        minute = minute.coerceIn(0, 59)
                    )
                )
            )
        }
        if (lower.contains("timer") || lower.contains("टाइमर")) {
            val num = Regex("(\\d+)").find(cleaned)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 5
            val seconds = if (lower.contains("second") || lower.contains("सेकंड")) num else num * 60
            return LocalCommandParseResult(
                handledLocally = true,
                replyText = if (isHindi) "$num मिनट का टाइमर सेट किया जा रहा है।" else "Setting a timer for $seconds seconds.",
                languageTag = lang,
                actions = listOf(
                    PendingAction(
                        id = uid(),
                        type = ActionType.SET_TIMER,
                        title = "Set Timer",
                        description = "Start countdown for $seconds seconds",
                        requiresConfirmation = false,
                        payloadText = "Jarvis VIP Timer",
                        seconds = seconds
                    )
                )
            )
        }

        // 11. Reminders, Tasks, Schedule & Birthdays
        if (lower.startsWith("remind me") || lower.startsWith("add task") || lower.startsWith("schedule") ||
            lower.contains("birthday") || lower.contains("याद दिलाओ") || lower.contains("जन्मदिन")
        ) {
            val category = when {
                lower.contains("birthday") || lower.contains("जन्मदिन") -> TaskCategory.BIRTHDAY
                lower.contains("schedule") || lower.contains("meeting") -> TaskCategory.SCHEDULE
                lower.contains("remind") || lower.contains("याद") -> TaskCategory.REMINDER
                else -> TaskCategory.TASK
            }
            val title = cleaned
                .replace(Regex("^(remind me to|add task|schedule|add birthday reminder for|याद दिलाओ)\\s*", RegexOption.IGNORE_CASE), "")
                .trim()
                .ifBlank { cleaned }
            return LocalCommandParseResult(
                handledLocally = true,
                replyText = if (isHindi) "मैंने इसे आपके प्लानर में जोड़ दिया है: $title" else "Added to your ${category.label.lowercase()} planner: $title",
                languageTag = lang,
                actions = listOf(
                    PendingAction(
                        id = uid(),
                        type = ActionType.CREATE_TASK,
                        title = "Save ${category.label}",
                        description = title,
                        requiresConfirmation = false,
                        payloadText = title,
                        taskCategory = category
                    )
                )
            )
        }

        // 12. Camera & Photos
        if (lower.contains("take photo") || lower.contains("capture photo") || lower.contains("open camera") || lower.contains("फोटो खींचो")) {
            return LocalCommandParseResult(
                handledLocally = true,
                replyText = if (isHindi) "कैमरा खोला जा रहा है।" else "Opening camera to capture a photo.",
                languageTag = lang,
                actions = listOf(
                    PendingAction(
                        id = uid(),
                        type = ActionType.CAPTURE_PHOTO,
                        title = "Capture Photo",
                        description = "Launch camera capture",
                        requiresConfirmation = false
                    )
                )
            )
        }
        if (lower.contains("pick photo") || lower.contains("select photo") || lower.contains("gallery") || lower.contains("फोटो चुनो")) {
            return LocalCommandParseResult(
                handledLocally = true,
                replyText = if (isHindi) "गैलरी से फोटो चुनने के लिए फोटो पिकर खोला जा रहा है।" else "Opening Android Photo Picker.",
                languageTag = lang,
                actions = listOf(
                    PendingAction(
                        id = uid(),
                        type = ActionType.PICK_PHOTO,
                        title = "Select Photo",
                        description = "Choose photo with zero-permission Photo Picker",
                        requiresConfirmation = false
                    )
                )
            )
        }

        // 13. Volume Controls
        if (lower.contains("volume") || lower.contains("mute") || lower.contains("आवाज़") || lower.contains("वॉल्यूम")) {
            val delta = when {
                lower.contains("up") || lower.contains("increase") || lower.contains("high") || lower.contains("बढ़ाओ") -> 2
                lower.contains("down") || lower.contains("decrease") || lower.contains("low") || lower.contains("कम") -> -2
                lower.contains("mute") || lower.contains("silent") || lower.contains("बंद") -> -15
                else -> 1
            }
            return LocalCommandParseResult(
                handledLocally = true,
                replyText = if (isHindi) "मीडिया वॉल्यूम समायोजित किया जा रहा है।" else "Adjusting media volume.",
                languageTag = lang,
                actions = listOf(
                    PendingAction(
                        id = uid(),
                        type = ActionType.ADJUST_VOLUME,
                        title = "Adjust Volume",
                        description = "Update device media volume",
                        requiresConfirmation = false,
                        volumeDelta = delta
                    )
                )
            )
        }

        // 14. Settings Shortcuts (Wi-Fi, Bluetooth, Phone Settings, Battery)
        if (lower.contains("wi-fi") || lower.contains("wifi") || lower.contains("वाई-फाई")) {
            return LocalCommandParseResult(
                handledLocally = true,
                replyText = if (isHindi) "Wi-Fi सेटिंग्स पैनल खोला जा रहा है।" else "Opening Wi-Fi quick settings panel.",
                languageTag = lang,
                actions = listOf(PendingAction(uid(), ActionType.OPEN_WIFI_SETTINGS, "Wi-Fi Settings", "Open system internet panel", false))
            )
        }
        if (lower.contains("bluetooth") || lower.contains("ब्लूटूथ")) {
            return LocalCommandParseResult(
                handledLocally = true,
                replyText = if (isHindi) "Bluetooth सेटिंग्स खोली जा रही हैं।" else "Opening Bluetooth settings.",
                languageTag = lang,
                actions = listOf(PendingAction(uid(), ActionType.OPEN_BLUETOOTH_SETTINGS, "Bluetooth Settings", "Open device Bluetooth settings", false))
            )
        }
        if (lower.contains("phone settings") || lower.equals("settings") || lower.contains("सेटिंग्स खोलो")) {
            return LocalCommandParseResult(
                handledLocally = true,
                replyText = if (isHindi) "फ़ोन सेटिंग्स खोली जा रही हैं।" else "Opening phone settings.",
                languageTag = lang,
                actions = listOf(PendingAction(uid(), ActionType.OPEN_PHONE_SETTINGS, "Phone Settings", "Open Android system settings", false))
            )
        }

        // 15. Navigation / Close App via Opt-In Accessibility
        if (lower == "go home" || lower == "home screen" || lower == "close app" || lower.contains("होम पर जाओ")) {
            return LocalCommandParseResult(
                handledLocally = true,
                replyText = if (isHindi) "होम स्क्रीन पर जा रहे हैं।" else "Navigating to Home screen.",
                languageTag = lang,
                actions = listOf(PendingAction(uid(), ActionType.NAVIGATION_HOME, "Go Home", "Return to Android Home screen", false))
            )
        }
        if (lower == "go back" || lower.contains("पीछे जाओ")) {
            return LocalCommandParseResult(
                handledLocally = true,
                replyText = if (isHindi) "पिछली स्क्रीन पर वापस जा रहे हैं।" else "Navigating back.",
                languageTag = lang,
                actions = listOf(PendingAction(uid(), ActionType.NAVIGATION_BACK, "Go Back", "Perform system Back navigation", false))
            )
        }

        // 16. Open Application
        val openMatch = Regex("^(?:open|launch|start|खोलो)\\s+(.+)$", RegexOption.IGNORE_CASE).find(cleaned)
            ?: Regex("^(.+?)\\s+(?:kholo|खोलो)$", RegexOption.IGNORE_CASE).find(cleaned)
        if (openMatch != null) {
            val appName = openMatch.groupValues[1].trim()
            return LocalCommandParseResult(
                handledLocally = true,
                replyText = if (isHindi) "$appName खोला जा रहा है।" else "Launching $appName.",
                languageTag = lang,
                actions = listOf(
                    PendingAction(
                        id = uid(),
                        type = ActionType.OPEN_APP,
                        title = "Open $appName",
                        description = "Launch $appName",
                        requiresConfirmation = false,
                        targetName = appName
                    )
                )
            )
        }

        // 17. Search / Knowledge
        if (lower.startsWith("search ") || lower.startsWith("google ") || lower.startsWith("who is ") || lower.startsWith("what is ") || lower.contains("सर्च करो")) {
            val query = cleaned.replace(Regex("^(search|google|who is|what is|सर्च करो)\\s*", RegexOption.IGNORE_CASE), "").trim()
            if (query.isNotBlank()) {
                return LocalCommandParseResult(
                    handledLocally = true,
                    languageTag = lang,
                    infoQuery = InfoQuery.Search(query)
                )
            }
        }

        // 18. Translation shortcut
        if (lower.startsWith("translate ")) {
            val toHindi = lower.contains("to hindi") || lower.contains("in hindi")
            val text = cleaned
                .replace(Regex("^translate\\s+", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s+(?:to|in)\\s+(?:hindi|english)$", RegexOption.IGNORE_CASE), "")
                .trim()
            return LocalCommandParseResult(
                handledLocally = true,
                languageTag = lang,
                infoQuery = InfoQuery.Translate(text, toHindi)
            )
        }

        // Otherwise route to OpenAI ChatGPT brain!
        return LocalCommandParseResult(handledLocally = false, languageTag = lang)
    }

    private fun uid(): String = UUID.randomUUID().toString()
}
