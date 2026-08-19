package com.jarvis.assistant.execution

import android.content.Context
import android.util.Log
import com.jarvis.assistant.accessibility.AccessibilityController
import com.jarvis.assistant.brain.JarvisIntent
import com.jarvis.assistant.device.AppController
import com.jarvis.assistant.device.CallController
import com.jarvis.assistant.device.MediaController
import com.jarvis.assistant.device.SmsController
import com.jarvis.assistant.device.SystemController

class CommandExecutor(private val context: Context? = null) {

    private val systemController = SystemController(context)
    private val appController = AppController(context)
    private val callController = CallController(context)
    private val smsController = SmsController(context)
    private val mediaController = MediaController(context)
    private val accessibilityController = AccessibilityController()

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
            is JarvisIntent.GetTime -> "Current time: ${systemController.getTime()}"
            is JarvisIntent.GetBattery -> "Battery level: ${systemController.getBatteryLevel()}"
            is JarvisIntent.OpenApp -> {
                val ok = appController.launchApp(intent.appName)
                if (ok) "App ${intent.appName} launched" else "Could not find or launch app ${intent.appName}"
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
            is JarvisIntent.Unknown -> "Routed command to cloud brain: \"${intent.raw}\""
        }
    }
}
