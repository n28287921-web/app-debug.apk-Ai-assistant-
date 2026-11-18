package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ActionLog
import com.example.data.model.AssistantTask
import com.example.data.model.ChatMessage
import com.example.data.model.LanguageMode
import com.example.data.model.TaskCategory
import com.example.data.model.TaskPriority
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class JarvisFirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    suspend fun ensureUserProfile(
        userId: String,
        displayName: String,
        email: String
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed." }
            val ref = userDoc(uid)
            val snap = ref.get().await()
            if (!snap.exists()) {
                val safeName = displayName.trim().ifBlank { "Jarvis User" }.take(100)
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to email.trim().take(160),
                    "preferredLanguage" to LanguageMode.BILINGUAL.firestoreValue,
                    "voiceEnabled" to true,
                    "handsFreeWakeWord" to false,
                    "backendBaseUrl" to "",
                    "openAiModel" to "gpt-4o-mini",
                    "accessibilityConsentGranted" to false,
                    "notificationListenerConsentGranted" to false,
                    "approvedPreferencesSummary" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                ref.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "users/$uid"
        emitAll(
            userDoc(uid)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun updateUserPreferences(
        userId: String,
        preferredLanguage: LanguageMode? = null,
        voiceEnabled: Boolean? = null,
        handsFreeWakeWord: Boolean? = null,
        backendBaseUrl: String? = null,
        openAiModel: String? = null,
        accessibilityConsentGranted: Boolean? = null,
        notificationListenerConsentGranted: Boolean? = null,
        approvedPreferencesSummary: String? = null
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
            preferredLanguage?.let { updates["preferredLanguage"] = it.firestoreValue }
            voiceEnabled?.let { updates["voiceEnabled"] = it }
            handsFreeWakeWord?.let { updates["handsFreeWakeWord"] = it }
            backendBaseUrl?.let { updates["backendBaseUrl"] = it.trim().take(300) }
            openAiModel?.let { updates["openAiModel"] = it.trim().ifBlank { "gpt-4o-mini" }.take(60) }
            accessibilityConsentGranted?.let { updates["accessibilityConsentGranted"] = it }
            notificationListenerConsentGranted?.let { updates["notificationListenerConsentGranted"] = it }
            approvedPreferencesSummary?.let { updates["approvedPreferencesSummary"] = it.trim().take(1000) }
            userDoc(uid).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeChatMessages(userId: String): Flow<List<ChatMessage>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "users/$uid/chatMessages"
        emitAll(
            userDoc(uid).collection("chatMessages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .limitToLast(80)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addChatMessage(
        userId: String,
        role: String,
        content: String,
        languageTag: String = "bilingual",
        actionSummary: String? = null,
        actionStatus: String = "none"
    ): Result<String> {
        val path = "users/$userId/chatMessages"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val cleanContent = content.trim().take(8000)
            require(cleanContent.isNotEmpty()) { "Message content cannot be empty." }
            val safeRole = if (role in listOf("user", "assistant", "system")) role else "user"
            val safeLang = if (languageTag in listOf("en", "hi", "bilingual")) languageTag else "bilingual"
            val safeStatus = if (actionStatus in listOf("none", "pending_confirm", "executed", "failed", "cancelled")) actionStatus else "none"
            val docRef = userDoc(uid).collection("chatMessages").document()
            val payload = mutableMapOf<String, Any>(
                "role" to safeRole,
                "content" to cleanContent,
                "languageTag" to safeLang,
                "actionStatus" to safeStatus,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!actionSummary.isNullOrBlank()) {
                payload["actionSummary"] = actionSummary.trim().take(500)
            }
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    fun observeTasks(userId: String): Flow<List<AssistantTask>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "users/$uid/tasks"
        emitAll(
            userDoc(uid).collection("tasks")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(AssistantTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addTask(
        userId: String,
        title: String,
        notes: String = "",
        category: TaskCategory = TaskCategory.TASK,
        dueDateLabel: String = "",
        dueTimeLabel: String = "",
        priority: TaskPriority = TaskPriority.NORMAL
    ): Result<String> {
        val path = "users/$userId/tasks"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val cleanTitle = title.trim().take(200)
            require(cleanTitle.isNotEmpty()) { "Task title cannot be empty." }
            val docRef = userDoc(uid).collection("tasks").document()
            val payload = mapOf(
                "title" to cleanTitle,
                "notes" to notes.trim().take(1000),
                "category" to category.firestoreValue,
                "dueDateLabel" to dueDateLabel.trim().take(60),
                "dueTimeLabel" to dueTimeLabel.trim().take(40),
                "completed" to false,
                "priority" to priority.firestoreValue,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleTaskCompleted(userId: String, taskId: String, completed: Boolean): Result<Unit> {
        val path = "users/$userId/tasks/$taskId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            require(taskId.isNotBlank())
            userDoc(uid).collection("tasks").document(taskId)
                .update(
                    mapOf(
                        "completed" to completed,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteTask(userId: String, taskId: String): Result<Unit> {
        val path = "users/$userId/tasks/$taskId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            require(taskId.isNotBlank())
            userDoc(uid).collection("tasks").document(taskId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    fun observeActionLogs(userId: String): Flow<List<ActionLog>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "users/$uid/actionLogs"
        emitAll(
            userDoc(uid).collection("actionLogs")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(30)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ActionLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun logAction(
        userId: String,
        commandText: String,
        actionType: String,
        targetSummary: String,
        status: String,
        detailMessage: String
    ): Result<Unit> {
        val path = "users/$userId/actionLogs"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val safeStatus = if (status in listOf("executed", "confirmed", "cancelled", "failed")) status else "executed"
            val payload = mapOf(
                "commandText" to commandText.trim().ifBlank { "Command" }.take(500),
                "actionType" to actionType.trim().ifBlank { "GENERAL" }.take(80),
                "targetSummary" to targetSummary.trim().take(300),
                "status" to safeStatus,
                "detailMessage" to detailMessage.trim().take(500),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            userDoc(uid).collection("actionLogs").document().set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }
}
