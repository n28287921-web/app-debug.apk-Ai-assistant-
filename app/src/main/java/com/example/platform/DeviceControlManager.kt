package com.example.platform

import android.app.ActivityManager
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SmsManager
import androidx.core.content.FileProvider
import com.example.data.model.ContactMatch
import com.example.data.model.DeviceTelemetry
import com.example.data.model.InstalledAppInfo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeviceControlManager(private val context: Context) {

    fun getDeviceTelemetry(): DeviceTelemetry {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 0
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempC = tempTenths / 10f

        val statFs = StatFs(Environment.getDataDirectory().path)
        val totalBytes = statFs.blockCountLong * statFs.blockSizeLong
        val availBytes = statFs.availableBlocksLong * statFs.blockSizeLong
        val gbDivisor = 1024.0 * 1024.0 * 1024.0

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNet = cm.activeNetwork
        val caps = if (activeNet != null) cm.getNetworkCapabilities(activeNet) else null
        val wifiConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val btEnabled = try {
            btManager?.adapter?.isEnabled == true
        } catch (_: SecurityException) {
            false
        }

        return DeviceTelemetry(
            batteryPercent = pct,
            charging = charging,
            batteryTempC = tempC,
            freeStorageGb = ((availBytes / gbDivisor) * 10).toInt() / 10.0,
            totalStorageGb = ((totalBytes / gbDivisor) * 10).toInt() / 10.0,
            availableRamGb = ((memInfo.availMem / gbDivisor) * 10).toInt() / 10.0,
            totalRamGb = ((memInfo.totalMem / gbDivisor) * 10).toInt() / 10.0,
            wifiEnabled = wifiConnected,
            bluetoothEnabled = btEnabled,
            mediaVolumePercent = getMediaVolumePercent(),
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        )
    }

    fun getMediaVolumePercent(): Int {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        return (current * 100) / max
    }

    fun setMediaVolumePercent(percent: Int): Int {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val target = ((percent.coerceIn(0, 100) / 100f) * max).toInt().coerceIn(0, max)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
        return (target * 100) / max
    }

    fun adjustMediaVolume(steps: Int): Int {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val next = (current + steps).coerceIn(0, max)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, next, AudioManager.FLAG_SHOW_UI)
        return (next * 100) / max
    }

    fun queryInstalledApps(): List<InstalledAppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        return activities.mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            if (pkg == context.packageName) return@mapNotNull null
            val label = info.loadLabel(pm).toString()
            InstalledAppInfo(appName = label, packageName = pkg)
        }.distinctBy { it.packageName }.sortedBy { it.appName.lowercase() }
    }

    fun launchApp(queryOrPackage: String): Result<String> {
        val pm = context.packageManager
        val directIntent = pm.getLaunchIntentForPackage(queryOrPackage)
        if (directIntent != null) {
            directIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(directIntent)
            return Result.success("Launched $queryOrPackage")
        }

        val apps = queryInstalledApps()
        val match = apps.firstOrNull { it.appName.equals(queryOrPackage, ignoreCase = true) }
            ?: apps.firstOrNull { it.appName.contains(queryOrPackage, ignoreCase = true) }
            ?: apps.firstOrNull { it.packageName.contains(queryOrPackage, ignoreCase = true) }

        if (match != null) {
            val intent = pm.getLaunchIntentForPackage(match.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return Result.success("Opened ${match.appName}")
            }
        }
        return Result.failure(IllegalStateException("App '$queryOrPackage' was not found on this device."))
    }

    fun openAppInfoSettings(packageName: String): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun searchContacts(query: String): List<ContactMatch> {
        val results = mutableListOf<ContactMatch>()
        val cleanQuery = query.trim()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = if (cleanQuery.isNotBlank()) {
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        } else null
        val selectionArgs = if (cleanQuery.isNotBlank()) arrayOf("%$cleanQuery%") else null

        context.contentResolver.query(
            uri,
            projection,
            selection,
            selectionArgs,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext() && results.size < 25) {
                val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else ""
                val num = if (numIdx >= 0) cursor.getString(numIdx).orEmpty() else ""
                if (name.isNotBlank() && num.isNotBlank()) {
                    results += ContactMatch(name, num)
                }
            }
        }
        return results.distinctBy { "${it.name}|${it.phoneNumber.replace(" ", "")}" }
    }

    fun placeCall(phoneNumber: String, hasCallPermission: Boolean): Result<String> {
        return try {
            val clean = phoneNumber.trim()
            require(clean.isNotBlank()) { "Phone number is empty." }
            val action = if (hasCallPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
            val intent = Intent(action, Uri.parse("tel:${Uri.encode(clean)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.success(if (hasCallPermission) "Calling $clean" else "Opened dialer for $clean")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun sendSms(phoneNumber: String, message: String, hasSmsPermission: Boolean): Result<String> {
        return try {
            val cleanNum = phoneNumber.trim()
            val cleanMsg = message.trim()
            if (hasSmsPermission && cleanNum.isNotBlank() && cleanMsg.isNotBlank()) {
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                smsManager.sendTextMessage(cleanNum, null, cleanMsg, null, null)
                Result.success("SMS sent to $cleanNum")
            } else {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(cleanNum)}")).apply {
                    putExtra("sms_body", cleanMsg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Result.success("Opened SMS composer for $cleanNum")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openWhatsAppDraft(phoneNumberOrBlank: String, message: String): Result<String> {
        return try {
            val digits = phoneNumberOrBlank.filter { it.isDigit() }
            val intent = if (digits.isNotBlank()) {
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://wa.me/$digits?text=${Uri.encode(message)}")
                )
            } else {
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    setPackage("com.whatsapp")
                }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
                val chooser = Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, message)
                    },
                    "Share WhatsApp Draft"
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(chooser)
            }
            Result.success("WhatsApp draft opened")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openFacebook(): Result<String> {
        return try {
            val launch = context.packageManager.getLaunchIntentForPackage("com.facebook.katana")
                ?: context.packageManager.getLaunchIntentForPackage("com.facebook.lite")
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
                Result.success("Opened Facebook app")
            } else {
                val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://m.facebook.com")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(web)
                Result.success("Opened Facebook web")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun shareToFacebook(caption: String, imageUri: Uri?): Result<String> {
        return try {
            if (caption.isNotBlank()) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Jarvis Facebook Caption", caption))
            }
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                if (imageUri != null) {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, imageUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } else {
                    type = "text/plain"
                }
                if (caption.isNotBlank()) {
                    putExtra(Intent.EXTRA_TEXT, caption)
                }
                val hasFb = context.packageManager.getLaunchIntentForPackage("com.facebook.katana") != null
                if (hasFb) setPackage("com.facebook.katana")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(sendIntent, "Share to Facebook").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            Result.success("Facebook share composer opened (caption copied to clipboard)")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun setSystemAlarm(hour: Int, minute: Int, label: String): Result<String> {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23))
                putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
                putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "Jarvis VIP Alarm" })
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.success(String.format("Alarm set for %02d:%02d", hour, minute))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun setSystemTimer(seconds: Int, label: String): Result<String> {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds.coerceAtLeast(1))
                putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "Jarvis VIP Timer" })
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.success("Timer started for $seconds seconds")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun addCalendarEvent(title: String, notes: String): Result<String> {
        return try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                putExtra(CalendarContract.Events.DESCRIPTION, notes)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.success("Opened Calendar event editor")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openWebSearch(query: String): Result<String> {
        return try {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
            Result.success("Opened web search for $query")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openWifiSettingsPanel(): Result<String> {
        return try {
            val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Settings.Panel.ACTION_INTERNET_CONNECTIVITY
            } else {
                Settings.ACTION_WIFI_SETTINGS
            }
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Result.success("Opened Wi-Fi & Internet settings")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openBluetoothSettings(): Result<String> {
        return try {
            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Result.success("Opened Bluetooth settings")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openPhoneSettings(): Result<String> {
        return try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Result.success("Opened device settings")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openBatteryOptimizationSettings(): Result<String> {
        return try {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            Result.success("Opened Battery Optimization settings")
        } catch (e: Exception) {
            openPhoneSettings()
        }
    }

    fun openAccessibilitySettings(): Result<String> {
        return try {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            Result.success("Opened Accessibility settings")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openNotificationListenerSettings(): Result<String> {
        return try {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            Result.success("Opened Notification Access settings")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun createTempCameraUri(): Uri {
        val cameraDir = File(context.cacheDir, "camera").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(cameraDir, "JARVIS_$stamp.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
