package com.example.data.model

import android.net.Uri

enum class AssistantCoreState {
    STANDBY,
    LISTENING,
    WAKE_WORD_ACTIVE,
    THINKING,
    SPEAKING
}

enum class ActionType {
    OPEN_APP,
    CLOSE_APP_GUIDE,
    CALL_CONTACT,
    SEND_SMS,
    WHATSAPP_DRAFT,
    FACEBOOK_POST,
    OPEN_FACEBOOK,
    SET_ALARM,
    SET_TIMER,
    CREATE_TASK,
    ADD_CALENDAR_EVENT,
    ADJUST_VOLUME,
    OPEN_WIFI_SETTINGS,
    OPEN_BLUETOOTH_SETTINGS,
    OPEN_PHONE_SETTINGS,
    OPEN_BATTERY_OPTIMIZATION,
    CAPTURE_PHOTO,
    PICK_PHOTO,
    READ_NOTIFICATIONS,
    BATTERY_STORAGE_STATUS,
    WEATHER_UPDATE,
    DAILY_NEWS,
    WEB_SEARCH,
    TRANSLATE,
    CALCULATE,
    NAVIGATION_BACK,
    NAVIGATION_HOME,
    NAVIGATION_RECENTS,
    CHAT_ONLY
}

data class PendingAction(
    val id: String,
    val type: ActionType,
    val title: String,
    val description: String,
    val requiresConfirmation: Boolean,
    val targetName: String = "",
    val targetPhoneOrPackage: String = "",
    val payloadText: String = "",
    val hour: Int = 7,
    val minute: Int = 0,
    val seconds: Int = 60,
    val volumeDelta: Int = 0,
    val taskCategory: TaskCategory = TaskCategory.TASK,
    val attachmentUri: Uri? = null
)

data class DeviceTelemetry(
    val batteryPercent: Int = 0,
    val charging: Boolean = false,
    val batteryTempC: Float = 0f,
    val freeStorageGb: Double = 0.0,
    val totalStorageGb: Double = 0.0,
    val availableRamGb: Double = 0.0,
    val totalRamGb: Double = 0.0,
    val wifiEnabled: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val mediaVolumePercent: Int = 50,
    val deviceModel: String = ""
)

data class ContactMatch(
    val name: String,
    val phoneNumber: String
)

data class InstalledAppInfo(
    val appName: String,
    val packageName: String
)

data class NotificationDigestItem(
    val id: String,
    val appName: String,
    val title: String,
    val body: String,
    val postedAtMillis: Long
)

data class WeatherInfo(
    val locationName: String,
    val temperatureC: Double,
    val windSpeedKmh: Double,
    val conditionLabel: String
)

data class NewsHeadline(
    val title: String,
    val source: String,
    val link: String
)
