package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import java.lang.ref.WeakReference

class JarvisAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRef = WeakReference(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Passive: Jarvis only performs explicit user-triggered global navigation actions.
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instanceRef?.clear()
        instanceRef = null
        super.onDestroy()
    }

    fun goHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun goBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun openRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun openNotificationsShade(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    companion object {
        private var instanceRef: WeakReference<JarvisAccessibilityService>? = null

        fun current(): JarvisAccessibilityService? = instanceRef?.get()

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            val cn = ComponentName(context, JarvisAccessibilityService::class.java)
            return enabled.contains(cn.flattenToString(), ignoreCase = true) ||
                enabled.contains(cn.flattenToShortString(), ignoreCase = true)
        }
    }
}
