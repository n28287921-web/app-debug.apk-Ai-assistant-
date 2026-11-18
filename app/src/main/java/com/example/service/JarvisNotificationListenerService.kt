package com.example.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.data.model.NotificationDigestItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class JarvisNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val item = sbn ?: return
        if (item.packageName == packageName) return
        val extras = item.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
        val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty().trim()
        if (title.isBlank() && body.isBlank()) return

        val appName = try {
            val ai = packageManager.getApplicationInfo(item.packageName, 0)
            packageManager.getApplicationLabel(ai).toString()
        } catch (_: Exception) {
            item.packageName
        }

        val digest = NotificationDigestItem(
            id = "${item.packageName}:${item.id}:${item.postTime}",
            appName = appName,
            title = title,
            body = body,
            postedAtMillis = item.postTime
        )
        val current = _recentNotifications.value.toMutableList()
        current.removeAll { it.id == digest.id }
        current.add(0, digest)
        _recentNotifications.value = current.take(15)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val item = sbn ?: return
        _recentNotifications.value = _recentNotifications.value.filterNot {
            it.id.startsWith("${item.packageName}:${item.id}:")
        }
    }

    companion object {
        private val _recentNotifications = MutableStateFlow<List<NotificationDigestItem>>(emptyList())
        val recentNotifications: StateFlow<List<NotificationDigestItem>> = _recentNotifications.asStateFlow()

        fun clear() {
            _recentNotifications.value = emptyList()
        }

        fun isEnabled(context: Context): Boolean {
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ).orEmpty()
            val cn = ComponentName(context, JarvisNotificationListenerService::class.java)
            return enabledListeners.contains(cn.flattenToString()) || enabledListeners.contains(cn.flattenToShortString())
        }
    }
}
