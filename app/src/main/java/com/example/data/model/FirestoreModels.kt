package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class LanguageMode(val firestoreValue: String, val displayLabel: String) {
    BILINGUAL("bilingual", "Auto (EN + हिन्दी)"),
    ENGLISH("en", "English"),
    HINDI("hi", "हिन्दी (Hindi)");

    companion object {
        fun fromRaw(raw: String?): LanguageMode =
            entries.firstOrNull { it.firestoreValue == raw } ?: BILINGUAL
    }
}

enum class TaskCategory(val firestoreValue: String, val label: String) {
    TASK("task", "Task"),
    REMINDER("reminder", "Reminder"),
    SCHEDULE("schedule", "Schedule"),
    BIRTHDAY("birthday", "Birthday");

    companion object {
        fun fromRaw(raw: String?): TaskCategory =
            entries.firstOrNull { it.firestoreValue == raw } ?: TASK
    }
}

enum class TaskPriority(val firestoreValue: String, val label: String) {
    LOW("low", "Low"),
    NORMAL("normal", "Normal"),
    HIGH("high", "High");

    companion object {
        fun fromRaw(raw: String?): TaskPriority =
            entries.firstOrNull { it.firestoreValue == raw } ?: NORMAL
    }
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val preferredLanguage: String = LanguageMode.BILINGUAL.firestoreValue,
    val voiceEnabled: Boolean = true,
    val handsFreeWakeWord: Boolean = false,
    val backendBaseUrl: String = "",
    val openAiModel: String = "gpt-4o-mini",
    val accessibilityConsentGranted: Boolean = false,
    val notificationListenerConsentGranted: Boolean = false,
    val approvedPreferencesSummary: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val role: String = "user",
    val content: String = "",
    val languageTag: String = "bilingual",
    val actionSummary: String = "",
    val actionStatus: String = "none",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AssistantTask(
    @DocumentId val id: String = "",
    val title: String = "",
    val notes: String = "",
    val category: String = TaskCategory.TASK.firestoreValue,
    val dueDateLabel: String = "",
    val dueTimeLabel: String = "",
    val completed: Boolean = false,
    val priority: String = TaskPriority.NORMAL.firestoreValue,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ActionLog(
    @DocumentId val id: String = "",
    val commandText: String = "",
    val actionType: String = "",
    val targetSummary: String = "",
    val status: String = "executed",
    val detailMessage: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
