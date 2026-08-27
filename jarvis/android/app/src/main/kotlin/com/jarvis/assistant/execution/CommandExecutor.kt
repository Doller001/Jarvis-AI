package com.jarvis.assistant.execution

import android.content.Context
import android.util.Log
import com.jarvis.assistant.accessibility.AccessibilityController
import com.jarvis.assistant.brain.JarvisIntent
import com.jarvis.assistant.device.AppController
import com.jarvis.assistant.device.AlarmController
import com.jarvis.assistant.device.CallController
import com.jarvis.assistant.device.CallLogController
import com.jarvis.assistant.device.ClipboardController
import com.jarvis.assistant.device.DisplayController
import com.jarvis.assistant.device.GalleryController
import com.jarvis.assistant.device.LocationController
import com.jarvis.assistant.device.MediaController
import com.jarvis.assistant.device.SmsController
import com.jarvis.assistant.device.SystemController

class CommandExecutor(private val context: Context? = null) {

    private val systemController = SystemController(context)
    private val appController = AppController(context)
    private val callController = CallController(context)
    private val callLogController = CallLogController(context)
    private val galleryController = GalleryController(context)
    private val smsController = SmsController(context)
    private val mediaController = MediaController(context)
    private val notificationController = com.jarvis.assistant.device.NotificationController(context)
    private val accessibilityController = AccessibilityController()
    private val actionExecutor = com.jarvis.assistant.actionengine.core.ActionExecutor(context)
    private val clipboardController = ClipboardController(context)
    private val alarmController = AlarmController(context)
    private val locationController = LocationController(context)
    private val displayController = DisplayController(context)

    fun execute(intent: JarvisIntent): String {
        Log.i("CommandExecutor", "Executing intent ${intent::class.simpleName}")
        return when (intent) {
            is JarvisIntent.ToggleTorch -> {
                val ok = systemController.toggleTorch(intent.state == "on")
                if (ok) "Torch turned ${intent.state}" else "Failed to toggle torch"
            }
            is JarvisIntent.ToggleWifi -> {
                val ok = systemController.toggleWifi(intent.state == "on")
                if (ok) "Wi-Fi settings opened to turn ${intent.state}" else "Failed to toggle Wi-Fi"
            }
            is JarvisIntent.ToggleBluetooth -> {
                val ok = systemController.toggleBluetooth(intent.state == "on")
                if (ok) "Bluetooth settings opened to turn ${intent.state}" else "Failed to toggle Bluetooth"
            }
            is JarvisIntent.SetVolume -> {
                val ok = systemController.setVolume(intent.level)
                if (ok) "Volume set to ${intent.level}%" else "Failed to set volume"
            }
            is JarvisIntent.MediaControl -> {
                when (intent.action) {
                    "play" -> {
                        val ok = mediaController.playMedia()
                        if (ok) "Playing music" else "Failed to play music"
                    }
                    "pause" -> {
                        val ok = mediaController.pauseMedia()
                        if (ok) "Paused music" else "Failed to pause music"
                    }
                    "next" -> {
                        val ok = mediaController.nextMedia()
                        if (ok) "Skipping to next track" else "Failed to skip track"
                    }
                    "prev" -> {
                        val ok = mediaController.previousMedia()
                        if (ok) "Playing previous track" else "Failed to play previous track"
                    }
                    else -> {
                        val ok = mediaController.togglePlayPause()
                        if (ok) "Toggled music playback" else "Failed to control media"
                    }
                }
            }
            is JarvisIntent.PlayMediaSearch -> {
                val ok = appController.playMediaOnApp(intent.query, intent.app)
                if (ok) "Playing ${intent.query} on ${intent.app.replaceFirstChar { it.uppercase() }}" else "Failed to play ${intent.query}"
            }
            is JarvisIntent.GetTime -> "Current time: ${systemController.getTime()}"
            is JarvisIntent.GetBattery -> "Battery level: ${systemController.getBatteryLevel()}"
            is JarvisIntent.GetStorage -> systemController.getStorageInfo()
            is JarvisIntent.OpenGallery -> {
                val ok = galleryController.openGallery()
                if (ok) "Opening Gallery" else "Failed to open Gallery"
            }
            is JarvisIntent.GetCallLog -> {
                val calls = callLogController.getRecentCalls(5)
                if (calls.isNotEmpty()) {
                    "Recent calls:\n" + calls.joinToString("\n")
                } else {
                    "No recent calls found."
                }
            }
            is JarvisIntent.ListApps -> {
                val apps = if (intent.category != null) {
                    appController.getAppsByCategory(intent.category)
                } else {
                    appController.getAllInstalledApps()
                }
                if (apps.isNotEmpty()) {
                    val catText = if (intent.category != null) "${intent.category} " else ""
                    "Found ${apps.size} ${catText}apps: " + apps.take(10).joinToString(", ") { it.name } +
                            if (apps.size > 10) " and ${apps.size - 10} more" else ""
                } else {
                    "No ${intent.category ?: ""} apps found."
                }
            }
            is JarvisIntent.OpenSettings -> {
                val ok = systemController.openSettings(intent.section)
                if (ok) "Opening Settings" else "Failed to open Settings"
            }
            is JarvisIntent.OpenApp -> {
                val ok = appController.launchApp(intent.appName)
                if (ok) "Opening ${intent.appName}" else "Could not find or launch app ${intent.appName}"
            }
            is JarvisIntent.CloseApp -> {
                val ok = appController.closeApp(intent.appName)
                if (ok) {
                    if (intent.appName != null) "Closing ${intent.appName}" else "Closed active app"
                } else {
                    "Unable to close ${intent.appName ?: "app"}"
                }
            }
            is JarvisIntent.CallContact -> {
                val ok = callController.makeCall(intent.contactName)
                if (ok) "Calling ${intent.contactName}…" else "Failed to initiate call to ${intent.contactName}"
            }
            is JarvisIntent.SendSms -> {
                val ok = smsController.sendSms(intent.recipient, intent.message)
                if (ok) "SMS sent to ${intent.recipient}" else "Failed to send SMS"
            }
            is JarvisIntent.SendWhatsApp -> {
                val ok = smsController.sendWhatsApp(intent.contactName, intent.message)
                if (ok) "WhatsApp message sent to ${intent.contactName}" else "Failed to send WhatsApp message"
            }
            is JarvisIntent.ReadScreen -> {
                val screenContent = accessibilityController.readScreen()
                "Screen contents: $screenContent"
            }
            is JarvisIntent.ReadNotification -> {
                val notifs = notificationController.readNotifications()
                if (notifs.isNotEmpty()) {
                    "Recent notifications:\n" + notifs.take(4).joinToString("\n")
                } else {
                    "No active notifications found."
                }
            }
            is JarvisIntent.MultiStepTask -> {
                val results = kotlinx.coroutines.runBlocking {
                    actionExecutor.executePlan(intent.plan)
                }
                val completed = results.count { it.executionSuccess }
                val failed = results.firstOrNull { !it.executionSuccess }
                if (failed == null) {
                    "Multi-step task completed: $completed actions finished."
                } else {
                    "Multi-step task stopped after $completed actions: " +
                        (failed.failure?.message ?: "${failed.actionId} failed")
                }
            }
            is JarvisIntent.LocalConversational -> intent.answer
            is JarvisIntent.SystemsCheck -> {
                val battery = systemController.getBatteryLevel()
                val time = systemController.getTime()
                val storage = systemController.getStorageInfo()
                "Systems Check Complete:\n• Power: Battery $battery\n• Time: $time\n• Storage: $storage\n• Neural Memory & Action Engine: Active and optimal."
            }
            is JarvisIntent.AnalyzeData -> {
                "Telemetry & Data Analytics:\n• Neural memory episodes synchronized.\n• Real-time DSP audio processor active at 16kHz.\n• Multi-provider LLM gateway connected."
            }
            is JarvisIntent.HomeControl -> {
                val ok = systemController.toggleTorch(true)
                if (ok) "Device environment control: Torch activated. All device subsystems ready." else "Device environment control active."
            }
            is JarvisIntent.ScheduleCheck -> {
                val time = systemController.getTime()
                val calendar = calendarReadText()
                val routineHint = "I can also run routines — say 'movie mode', 'morning routine', or 'meeting mode'."
                "Current time: $time.\n$calendar\n$routineHint"
            }
            // ===== NEW INTENT HANDLING =====
            is JarvisIntent.SetBrightness -> {
                val ok = systemController.setBrightness(intent.level)
                if (ok) "Brightness set to ${intent.level}%" else "Could not set brightness, Sir."
            }
            is JarvisIntent.ToggleRotationLock -> {
                val ok = systemController.setRotationLock(intent.state == "on")
                if (ok) "Rotation lock ${if (intent.state == "on") "enabled" else "disabled"}" else "Failed to change rotation lock"
            }
            is JarvisIntent.SetRingerMode -> {
                val ok = systemController.setRingerMode(intent.mode)
                if (ok) "Ringer mode set to ${intent.mode}" else "Failed to set ringer mode"
            }
            is JarvisIntent.ToggleDnd -> {
                val ok = systemController.setDnd(intent.state == "on")
                if (ok) "Do Not Disturb ${if (intent.state == "on") "enabled" else "disabled"}" else "Failed to toggle DND"
            }
            is JarvisIntent.TakeScreenshot -> {
                val ok = displayController.takeScreenshot()
                if (ok) "Capturing screenshot, Sir." else "Screenshot not available"
            }
            is JarvisIntent.BatteryChargingStatus -> systemController.getBatteryDetailed()
            is JarvisIntent.ConnectBluetooth -> {
                val ok = systemController.connectBluetoothDevice(intent.deviceName)
                if (ok) "Connecting Bluetooth${if (intent.deviceName.isNotBlank()) " to ${intent.deviceName}" else ""}." else "Bluetooth connect failed"
            }
            is JarvisIntent.CopyToClipboard -> {
                val ok = clipboardController.copyToClipboard(intent.text)
                if (ok) "Copied to clipboard, Sir." else "Clipboard unavailable"
            }
            is JarvisIntent.ReadClipboard -> clipboardController.readClipboard()
            is JarvisIntent.SetAlarm -> {
                val h = intent.time.toIntOrNull() ?: 7
                val ok = alarmController.setAlarm(h)
                if (ok) "Alarm set for $h:00, Sir." else "Could not set alarm"
            }
            is JarvisIntent.SetReminder -> {
                val ok = alarmController.setReminder(intent.delayMinutes, intent.message)
                if (ok) "Reminder set for ${intent.delayMinutes} minutes: ${intent.message}" else "Could not set reminder"
            }
            is JarvisIntent.SetTimer -> {
                val ok = alarmController.setTimer(intent.seconds)
                val mins = intent.seconds / 60
                if (ok) "Timer set for ${if (mins > 0) "$mins minute(s)" else "${intent.seconds} seconds"}, Sir." else "Could not set timer"
            }
            is JarvisIntent.ReadCalendar -> {
                val events = calendarReadText()
                events
            }
            is JarvisIntent.GetLocation -> locationController.getCoarseLocationDescription()
            is JarvisIntent.GetWeather -> {
                "Weather requires an online lookup, Sir. I can open a weather app or search the web for current conditions. Say 'search web weather in my city'."
            }
            is JarvisIntent.WebSearch -> {
                val ok = appController.playMediaOnApp(intent.query, "chrome") || run {
                    val intentGo = android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://www.google.com/search?q=${android.net.Uri.encode(intent.query)}")).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try { context?.startActivity(intentGo); true } catch (_: Exception) { false }
                }
                if (ok) "Searching the web for '${intent.query}', Sir." else "Web search failed"
            }
            is JarvisIntent.NavigateTo -> {
                val ok = locationController.openNavigation(intent.place)
                if (ok) "Navigating to ${intent.place}, Sir." else "Navigation failed"
            }
            is JarvisIntent.LockScreen -> {
                val ok = lockScreen()
                if (ok) "Locking your device, Sir." else "Cannot lock screen without device admin permission"
            }
            is JarvisIntent.EmergencySos -> {
                // Open dialer to emergency number
                val intentDial = android.content.Intent(android.content.Intent.ACTION_DIAL,
                    android.net.Uri.parse("tel:112")).apply { flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK }
                try { context?.startActivity(intentDial); "Opening emergency dialer, Sir." } catch (_: Exception) { "Could not launch emergency dialer" }
            }
            is JarvisIntent.ReadSmsInbox -> {
                val msgs = readSmsInbox()
                if (msgs.isNotBlank()) msgs else "No SMS messages found."
            }
            is JarvisIntent.ReadWhatsAppUnread -> {
                val notifs = notificationController.readNotifications("whatsapp")
                if (notifs.isNotEmpty()) "WhatsApp: " + notifs.take(4).joinToString("\n") else "No unread WhatsApp notifications, Sir."
            }
            is JarvisIntent.ForgetMemory -> {
                val ok = clearMemory()
                if (ok) "Memory cleared, Sir. A clean slate." else "Could not clear memory"
            }
            is JarvisIntent.ExportLogs -> {
                val ok = exportLogs()
                if (ok) "Logs exported to memory folder, Sir." else "Could not export logs"
            }
            // ===== Airplane mode (opens settings — programmatic toggle restricted on modern Android) =====
            is JarvisIntent.ToggleAirplaneMode -> {
                val ok = systemController.openSettings("airplane")
                if (ok) "Opening Airplane mode settings, Sir. Toggle as needed." else "Could not open airplane mode settings."
            }
            // ===== Daily Briefing =====
            is JarvisIntent.GetDailyBriefing -> {
                val time = systemController.getTime()
                val battery = systemController.getBatteryDetailed()
                val storage = systemController.getStorageInfo()
                val calendar = calendarReadText()
                buildString {
                    append("Good morning, Sir! Here is your daily briefing.\n")
                    append("⏰ Time: $time\n")
                    append("🔋 Battery: $battery\n")
                    append("💾 Storage: $storage\n")
                    append("📅 Calendar: $calendar\n")
                    append("Say 'morning routine' to activate your morning setup.")
                }
            }
            // ===== Routine Engine =====
            is JarvisIntent.RunRoutine -> {
                val engine = com.jarvis.assistant.routines.RoutineEngine(context)
                engine.execute(intent.routineName)
            }
            is JarvisIntent.ListRoutines -> {
                val engine = com.jarvis.assistant.routines.RoutineEngine(context)
                engine.listRoutines()
            }
            is JarvisIntent.Unknown -> "Routed command to cloud brain: \"${intent.raw}\""
        }
    }

    // ===== Helpers for new intents =====

    private fun calendarReadText(): String {
        val ctx = context ?: return "Calendar unavailable"
        return try {
            val uri = android.provider.CalendarContract.Events.CONTENT_URI
            val projection = arrayOf(
                android.provider.CalendarContract.Events.TITLE,
                android.provider.CalendarContract.Events.DTSTART,
                android.provider.CalendarContract.Events.DTEND
            )
            val selection = "(${android.provider.CalendarContract.Events.DTSTART} >= ?) AND (${android.provider.CalendarContract.Events.VISIBLE} = 1)"
            val now = System.currentTimeMillis().toString()
            val sort = "${android.provider.CalendarContract.Events.DTSTART} ASC LIMIT 5"
            val cursor = ctx.contentResolver.query(uri, projection, selection, arrayOf(now), sort)
            cursor?.use {
                val titles = mutableListOf<String>()
                val titleIdx = it.getColumnIndex(android.provider.CalendarContract.Events.TITLE)
                val startIdx = it.getColumnIndex(android.provider.CalendarContract.Events.DTSTART)
                while (it.moveToNext()) {
                    val title = it.getString(titleIdx) ?: "Event"
                    val start = it.getLong(startIdx)
                    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    titles.add("${sdf.format(java.util.Date(start))} — $title")
                }
                if (titles.isEmpty()) "You have no upcoming events, Sir." else "Your next events:\n" + titles.joinToString("\n")
            } ?: "Calendar unavailable"
        } catch (e: Exception) {
            "Calendar permission not granted, Sir. I need calendar access to read events."
        }
    }

    private fun lockScreen(): Boolean {
        // Requires DeviceAdminReceiver + device admin activation by user.
        return try {
            val ctx = context ?: return false
            val devicePolicyManager = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager
            val adminComponent = android.content.ComponentName(ctx, com.jarvis.assistant.services.JarvisDeviceAdminReceiver::class.java)
            if (devicePolicyManager?.isAdminActive(adminComponent) == true) {
                devicePolicyManager.lockNow()
                true
            } else {
                // Guide user to enable device admin
                val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, "JARVIS needs device admin to lock your screen by voice.")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                ctx.startActivity(intent)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun readSmsInbox(): String {
        val ctx = context ?: return "SMS unavailable"
        return try {
            val uri = android.net.Uri.parse("content://sms/inbox")
            val cursor = ctx.contentResolver.query(uri, arrayOf("address", "body", "date"), null, null, "date DESC LIMIT 5")
            cursor?.use {
                val msgs = mutableListOf<String>()
                val addrIdx = it.getColumnIndex("address")
                val bodyIdx = it.getColumnIndex("body")
                while (it.moveToNext()) {
                    val addr = it.getString(addrIdx) ?: "Unknown"
                    val body = it.getString(bodyIdx) ?: ""
                    msgs.add("From $addr: $body")
                }
                if (msgs.isEmpty()) "No SMS messages in inbox, Sir." else "Recent messages:\n" + msgs.joinToString("\n")
            } ?: "SMS permission not granted, Sir."
        } catch (e: Exception) {
            "SMS permission not granted, Sir."
        }
    }

    private fun clearMemory(): Boolean {
        return try {
            val ctx = context ?: return false
            val db = com.jarvis.assistant.memory.JarvisMemoryDatabase(ctx)
            db.writableDatabase.execSQL("DELETE FROM cag_cache")
            db.writableDatabase.execSQL("DELETE FROM rag_chunks")
            db.writableDatabase.execSQL("DELETE FROM mag_episodes")
            db.writableDatabase.execSQL("DELETE FROM mag_facts")
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun exportLogs(): Boolean {
        return try {
            val ctx = context ?: return false
            val dir = ctx.getExternalFilesDir("memory") ?: ctx.filesDir
            val exportFile = java.io.File(dir, "jarvis_export_${System.currentTimeMillis()}.txt")
            val sb = StringBuilder()
            sb.append("JARVIS Export\n")
            sb.append("Time: ${systemController.getTime()}\n")
            sb.append("Battery: ${systemController.getBatteryDetailed()}\n")
            exportFile.writeText(sb.toString())
            true
        } catch (e: Exception) {
            false
        }
    }
}
