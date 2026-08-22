package com.jarvis.assistant.execution

import android.content.Context
import android.util.Log
import com.jarvis.assistant.accessibility.AccessibilityController
import com.jarvis.assistant.brain.JarvisIntent
import com.jarvis.assistant.device.AppController
import com.jarvis.assistant.device.CallController
import com.jarvis.assistant.device.CallLogController
import com.jarvis.assistant.device.GalleryController
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
                if (intent.appName != null) "Closing ${intent.appName}" else "Closed active app"
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
                kotlinx.coroutines.runBlocking {
                    actionExecutor.executePlan(intent.plan)
                }
                "Multi-step task executed: ${intent.plan.steps.size} actions completed."
            }
            is JarvisIntent.LocalConversational -> intent.answer
            is JarvisIntent.Unknown -> "Routed command to cloud brain: \"${intent.raw}\""
        }
    }
}
