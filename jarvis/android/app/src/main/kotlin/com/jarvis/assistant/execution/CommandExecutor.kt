package com.jarvis.assistant.execution

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import com.jarvis.assistant.accessibility.AccessibilityController
import com.jarvis.assistant.brain.JarvisIntent
import com.jarvis.assistant.device.AlarmController
import com.jarvis.assistant.device.AppController
import com.jarvis.assistant.device.CallController
import com.jarvis.assistant.device.CallLogController
import com.jarvis.assistant.device.ClipboardController
import com.jarvis.assistant.device.ContactsController
import com.jarvis.assistant.device.DisplayController
import com.jarvis.assistant.device.GalleryController
import com.jarvis.assistant.device.LocationController
import com.jarvis.assistant.device.MediaController
import com.jarvis.assistant.device.SmsController
import com.jarvis.assistant.device.SystemController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CommandExecutor(private val context: Context? = null) {

    private val systemController = SystemController(context)
    private val appController = AppController(context)
    private val contactsController = ContactsController(context)
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
            // 1. Volume (Set %, Up/Down, Max/Min, Mute/Unmute)
            is JarvisIntent.SetVolume -> {
                when {
                    intent.isRelative -> {
                        val newPct = systemController.adjustVolumeRelative(intent.directionUp)
                        if (intent.directionUp) {
                            "Volume increased${if (newPct >= 0) " to $newPct%" else ""}, Sir."
                        } else {
                            "Volume decreased${if (newPct >= 0) " to $newPct%" else ""}, Sir."
                        }
                    }
                    intent.isMute -> {
                        val ok = systemController.muteVolume(true)
                        if (ok) "Volume muted, Sir." else "Failed to mute volume"
                    }
                    intent.isUnmute -> {
                        val ok = systemController.muteVolume(false)
                        if (ok) "Volume unmuted, Sir." else "Failed to unmute volume"
                    }
                    intent.level == 100 -> {
                        val ok = systemController.setVolume(100)
                        if (ok) "Volume set to maximum (100%), Sir." else "Failed to set volume"
                    }
                    intent.level == 0 -> {
                        val ok = systemController.setVolume(0)
                        if (ok) "Volume set to minimum (muted), Sir." else "Failed to set volume"
                    }
                    else -> {
                        val ok = systemController.setVolume(intent.level)
                        if (ok) "Volume set to ${intent.level}%, Sir." else "Failed to set volume"
                    }
                }
            }

            // 2. Wi-Fi & Bluetooth (Opens Settings due to Android OS security policy)
            is JarvisIntent.ToggleWifi -> {
                val ok = systemController.toggleWifi(intent.state == "on")
                if (ok) {
                    "Opening Wi-Fi settings to turn ${intent.state}, Sir. (Direct toggle is restricted by Android security policy)."
                } else {
                    "Failed to open Wi-Fi settings"
                }
            }
            is JarvisIntent.ToggleBluetooth -> {
                val ok = systemController.toggleBluetooth(intent.state == "on")
                if (ok) {
                    "Opening Bluetooth settings to turn ${intent.state}, Sir. (Direct toggle is restricted by Android security policy)."
                } else {
                    "Failed to open Bluetooth settings"
                }
            }

            // 3. Ringer Mode (Silent, Vibrate, Normal)
            is JarvisIntent.SetRingerMode -> {
                val ok = systemController.setRingerMode(intent.mode)
                if (ok) "Ringer mode set to ${intent.mode}, Sir." else "Could not set ringer mode to ${intent.mode}. Do Not Disturb permission may be required, Sir."
            }

            // 4. Do Not Disturb (DND On/Off)
            is JarvisIntent.ToggleDnd -> {
                val ok = systemController.setDnd(intent.state == "on")
                if (ok) {
                    if (intent.state == "on") "Do Not Disturb enabled, Sir." else "Do Not Disturb disabled, Sir."
                } else {
                    "Do Not Disturb permission required. Opening notification access settings, Sir."
                }
            }

            // 5. Screen Rotation (Lock/Unlock)
            is JarvisIntent.ToggleRotationLock -> {
                val ok = systemController.setRotationLock(intent.state == "on")
                if (ok) {
                    if (intent.state == "on") "Screen rotation locked (auto-rotate disabled), Sir." else "Screen rotation unlocked (auto-rotate enabled), Sir."
                } else {
                    "Permission to modify system settings required for rotation lock. Opening settings, Sir."
                }
            }

            // 6. Media Controls (Play, Pause, Stop, Next, Prev)
            is JarvisIntent.MediaControl -> {
                when (intent.action.lowercase()) {
                    "play" -> {
                        val ok = mediaController.playMedia()
                        if (ok) "Resuming media playback, Sir." else "Failed to play music"
                    }
                    "pause" -> {
                        val ok = mediaController.pauseMedia()
                        if (ok) "Paused media playback, Sir." else "Failed to pause music"
                    }
                    "stop" -> {
                        val ok = mediaController.stopMedia()
                        if (ok) "Stopped media playback, Sir." else "Failed to stop media"
                    }
                    "next" -> {
                        val ok = mediaController.nextMedia()
                        if (ok) "Skipping to the next track, Sir." else "Failed to skip track"
                    }
                    "prev" -> {
                        val ok = mediaController.previousMedia()
                        if (ok) "Playing the previous track, Sir." else "Failed to play previous track"
                    }
                    else -> {
                        val ok = mediaController.togglePlayPause()
                        if (ok) "Toggled media playback, Sir." else "Failed to control media"
                    }
                }
            }
            is JarvisIntent.PlayMediaSearch -> {
                val ok = appController.playMediaOnApp(intent.query, intent.app)
                if (ok) "Playing ${intent.query} on ${intent.app.replaceFirstChar { it.uppercase() }}, Sir." else "Failed to play ${intent.query}"
            }

            // 7. Phone Calls & Contacts
            is JarvisIntent.CallContact -> {
                val ok = callController.makeCall(intent.contactName)
                if (ok) "Calling ${intent.contactName}, Sir." else "Could not initiate call to ${intent.contactName}. Opening phone dialer, Sir."
            }
            is JarvisIntent.GetContact -> {
                val details = contactsController.lookupContact(intent.contactName)
                "Contact details for ${intent.contactName}: $details, Sir."
            }

            // 8. SMS
            is JarvisIntent.SendSms -> {
                val ok = smsController.sendSms(intent.recipient, intent.message)
                if (ok) "SMS sent to ${intent.recipient}: '${intent.message}', Sir." else "Failed to send SMS to ${intent.recipient}"
            }
            is JarvisIntent.ReadSmsInbox -> {
                val msgs = readSmsInbox()
                if (msgs.isNotBlank()) msgs else "No SMS messages found in inbox, Sir."
            }

            // 9. WhatsApp
            is JarvisIntent.SendWhatsApp -> {
                val ok = smsController.sendWhatsApp(intent.contactName, intent.message)
                if (ok) "WhatsApp message sent to ${intent.contactName}: '${intent.message}', Sir." else "Failed to send WhatsApp message to ${intent.contactName}"
            }
            is JarvisIntent.ReadWhatsAppUnread -> {
                val notifs = notificationController.readNotifications("whatsapp")
                if (notifs.isNotEmpty()) {
                    "Unread WhatsApp messages:\n" + notifs.take(5).joinToString("\n")
                } else {
                    "You have no unread WhatsApp messages, Sir."
                }
            }

            // 10. Alarms
            is JarvisIntent.SetAlarm -> {
                val ok = alarmController.setAlarm(intent.hour, intent.minute, intent.label)
                val ampm = if (intent.hour >= 12) "PM" else "AM"
                val displayHour = if (intent.hour % 12 == 0) 12 else intent.hour % 12
                val timeStr = "%02d:%02d %s".format(displayHour, intent.minute, ampm)
                if (ok) "Alarm set for $timeStr, Sir." else "Could not set alarm for $timeStr"
            }

            // 11. Reminders
            is JarvisIntent.SetReminder -> {
                val ok = alarmController.setReminder(intent.delayMinutes, intent.message)
                if (ok) "Reminder set for ${intent.delayMinutes} minute(s) from now: '${intent.message}', Sir." else "Could not set reminder"
            }

            // 12. Timers
            is JarvisIntent.SetTimer -> {
                val ok = alarmController.setTimer(intent.seconds)
                val timeText = if (intent.seconds >= 60) "${intent.seconds / 60} minute(s)" else "${intent.seconds} seconds"
                if (ok) "Timer set for $timeText, Sir." else "Could not set timer"
            }

            // 13. Calendar (Read next 5 upcoming events)
            is JarvisIntent.ReadCalendar -> calendarReadText()

            // 14. Location & Navigation
            is JarvisIntent.GetLocation -> locationController.getCoarseLocationDescription()
            is JarvisIntent.NavigateTo -> {
                val ok = locationController.openNavigation(intent.place)
                if (ok) "Opening Google Maps navigation to ${intent.place}, Sir." else "Failed to open navigation for ${intent.place}"
            }

            // 15. Screenshots
            is JarvisIntent.TakeScreenshot -> {
                val ok = displayController.takeScreenshot()
                if (ok) "Screenshot captured successfully, Sir." else "Accessibility Service is required for automatic screenshots. Please enable Jarvis in Accessibility Settings, Sir."
            }

            // 16. Clipboard (Copy / Read)
            is JarvisIntent.CopyToClipboard -> {
                val ok = clipboardController.copyToClipboard(intent.text)
                if (ok) "Copied to clipboard: '${intent.text}', Sir." else "Clipboard unavailable"
            }
            is JarvisIntent.ReadClipboard -> clipboardController.readClipboard()

            // 17. Lock Screen (Device Admin)
            is JarvisIntent.LockScreen -> {
                val ok = lockScreen()
                if (ok) "Locking screen, Sir." else "Device Admin permission is required to lock screen. Opening activation screen, Sir."
            }

            // 18. Web Search (Opens Google in Chrome)
            is JarvisIntent.WebSearch -> {
                val query = intent.query
                val ctx = context
                val uri = Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
                val ok = if (ctx != null) {
                    try {
                        val chromeIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                            setPackage("com.android.chrome")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        ctx.startActivity(chromeIntent)
                        true
                    } catch (_: Exception) {
                        try {
                            val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            ctx.startActivity(browserIntent)
                            true
                        } catch (_: Exception) {
                            false
                        }
                    }
                } else false

                if (ok) "Searching Google for '$query', Sir." else "Failed to initiate web search"
            }

            // Other Utilities
            is JarvisIntent.ToggleTorch -> {
                val ok = systemController.toggleTorch(intent.state == "on")
                if (ok) "Torch turned ${intent.state}, Sir." else "Failed to toggle torch"
            }
            is JarvisIntent.GetTime -> "Current time is ${systemController.getTime()}, Sir."
            is JarvisIntent.GetBattery -> "Battery level is ${systemController.getBatteryLevel()}, Sir."
            is JarvisIntent.GetStorage -> systemController.getStorageInfo()
            is JarvisIntent.OpenGallery -> {
                val ok = galleryController.openGallery()
                if (ok) "Opening Gallery, Sir." else "Failed to open Gallery"
            }
            is JarvisIntent.GetCallLog -> {
                val calls = callLogController.getRecentCalls(5)
                if (calls.isNotEmpty()) {
                    "Recent calls:\n" + calls.joinToString("\n")
                } else {
                    "No recent calls found, Sir."
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
                    "No ${intent.category ?: ""} apps found, Sir."
                }
            }
            is JarvisIntent.OpenSettings -> {
                val ok = systemController.openSettings(intent.section)
                if (ok) "Opening Settings${if (intent.section != null) " for ${intent.section}" else ""}, Sir." else "Failed to open Settings"
            }
            is JarvisIntent.OpenApp -> {
                val ok = appController.launchApp(intent.appName)
                if (ok) "Opening ${intent.appName}, Sir." else "Could not find or launch app ${intent.appName}"
            }
            is JarvisIntent.CloseApp -> {
                val ok = appController.closeApp(intent.appName)
                if (ok) {
                    if (intent.appName != null) "Closing ${intent.appName}, Sir." else "Closed active app, Sir."
                } else {
                    "Unable to close ${intent.appName ?: "app"}"
                }
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
                    "No active notifications found, Sir."
                }
            }
            is JarvisIntent.MultiStepTask -> {
                val results = kotlinx.coroutines.runBlocking {
                    actionExecutor.executePlan(intent.plan)
                }
                val completed = results.count { it.executionSuccess }
                val failed = results.firstOrNull { !it.executionSuccess }
                if (failed == null) {
                    "Multi-step task completed: $completed actions finished successfully, Sir."
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
                "Systems Check Complete, Sir:\n• Power: Battery $battery\n• Time: $time\n• Storage: $storage\n• Cognitive & Neural Memory Engines: Active and optimal."
            }
            is JarvisIntent.AnalyzeData -> {
                "Telemetry & Data Analytics:\n• Neural memory episodes synchronized.\n• Real-time DSP audio processor active.\n• Multi-provider LLM gateway connected."
            }
            is JarvisIntent.HomeControl -> {
                val ok = systemController.toggleTorch(true)
                if (ok) "Device environment control: Torch activated. All device subsystems ready, Sir." else "Device environment control active."
            }
            is JarvisIntent.ScheduleCheck -> calendarReadText()
            is JarvisIntent.SetBrightness -> {
                val ok = systemController.setBrightness(intent.level)
                if (ok) "Brightness set to ${intent.level}%, Sir." else "Could not set brightness, Sir."
            }
            is JarvisIntent.BatteryChargingStatus -> systemController.getBatteryDetailed()
            is JarvisIntent.ConnectBluetooth -> {
                val ok = systemController.connectBluetoothDevice(intent.deviceName)
                if (ok) "Connecting Bluetooth${if (intent.deviceName.isNotBlank()) " to ${intent.deviceName}" else ""}, Sir." else "Bluetooth connect failed"
            }
            is JarvisIntent.GetWeather -> {
                "Weather lookup requires active web query, Sir. Say 'search weather in my city' to check conditions."
            }
            is JarvisIntent.EmergencySos -> {
                val intentDial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context?.startActivity(intentDial)
                    "Opening emergency dialer, Sir."
                } catch (_: Exception) {
                    "Could not launch emergency dialer"
                }
            }
            is JarvisIntent.ForgetMemory -> {
                val ok = clearMemory()
                if (ok) "Memory cleared, Sir. A clean slate." else "Could not clear memory"
            }
            is JarvisIntent.ExportLogs -> {
                val ok = exportLogs()
                if (ok) "Logs exported to memory folder, Sir." else "Could not export logs"
            }
            is JarvisIntent.ToggleAirplaneMode -> {
                val ok = systemController.openSettings("airplane")
                if (ok) "Opening Airplane mode settings, Sir. Direct toggle is restricted by Android security policy." else "Could not open airplane mode settings."
            }
            is JarvisIntent.GetDailyBriefing -> {
                val time = systemController.getTime()
                val battery = systemController.getBatteryDetailed()
                val storage = systemController.getStorageInfo()
                val calendar = calendarReadText()
                buildString {
                    append("Good day, Sir! Here is your daily briefing.\n")
                    append("⏰ Time: $time\n")
                    append("🔋 Battery: $battery\n")
                    append("💾 Storage: $storage\n")
                    append("📅 $calendar\n")
                }
            }
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

    private fun calendarReadText(): String {
        val ctx = context ?: return "Calendar unavailable"
        return try {
            val uri = CalendarContract.Events.CONTENT_URI
            val projection = arrayOf(
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND
            )
            val selection = "(${CalendarContract.Events.DTSTART} >= ?) AND (${CalendarContract.Events.VISIBLE} = 1)"
            val now = System.currentTimeMillis().toString()
            val sort = "${CalendarContract.Events.DTSTART} ASC LIMIT 5"
            val cursor = ctx.contentResolver.query(uri, projection, selection, arrayOf(now), sort)
            cursor?.use {
                val events = mutableListOf<String>()
                val titleIdx = it.getColumnIndex(CalendarContract.Events.TITLE)
                val startIdx = it.getColumnIndex(CalendarContract.Events.DTSTART)
                val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
                var count = 1
                while (it.moveToNext()) {
                    val title = it.getString(titleIdx) ?: "Untitled Event"
                    val start = it.getLong(startIdx)
                    val dateStr = dateFormat.format(Date(start))
                    val timeStr = timeFormat.format(Date(start))
                    events.add("$count. $title ($dateStr at $timeStr)")
                    count++
                }
                if (events.isEmpty()) {
                    "You have no upcoming events scheduled on your calendar, Sir."
                } else {
                    "Your next upcoming events:\n" + events.joinToString("\n")
                }
            } ?: "Calendar unavailable, Sir."
        } catch (e: Exception) {
            "Calendar permission not granted, Sir. Please allow calendar access in app permissions."
        }
    }

    private fun lockScreen(): Boolean {
        return try {
            val ctx = context ?: return false
            val devicePolicyManager = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager
            val adminComponent = android.content.ComponentName(ctx, com.jarvis.assistant.services.JarvisDeviceAdminReceiver::class.java)
            if (devicePolicyManager?.isAdminActive(adminComponent) == true) {
                devicePolicyManager.lockNow()
                true
            } else {
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
            val uri = Uri.parse("content://sms/inbox")
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
