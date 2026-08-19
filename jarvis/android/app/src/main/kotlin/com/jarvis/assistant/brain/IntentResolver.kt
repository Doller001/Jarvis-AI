package com.jarvis.assistant.brain

sealed class JarvisIntent {
    data class GetTime(val raw: String) : JarvisIntent()
    data class GetBattery(val raw: String) : JarvisIntent()
    data class ToggleTorch(val state: String) : JarvisIntent()
    data class ToggleWifi(val state: String) : JarvisIntent()
    data class ToggleBluetooth(val state: String) : JarvisIntent()
    data class SetVolume(val level: Int) : JarvisIntent()
    data class OpenApp(val appName: String) : JarvisIntent()
    data class ReadScreen(val target: String = "screen") : JarvisIntent()
    data class CallContact(val contactName: String) : JarvisIntent()
    data class SendSms(val recipient: String, val message: String) : JarvisIntent()
    data class Unknown(val raw: String) : JarvisIntent()
}

class IntentResolver {
    fun resolve(rawText: String): JarvisIntent {
        val t = rawText.lowercase().strip()

        if (t.contains("time")) return JarvisIntent.GetTime(rawText)
        if (t.contains("battery")) return JarvisIntent.GetBattery(rawText)

        if (t.contains("torch on") || t.contains("flashlight on")) return JarvisIntent.ToggleTorch("on")
        if (t.contains("torch off") || t.contains("flashlight off")) return JarvisIntent.ToggleTorch("off")

        if (t.contains("wifi on") || t.contains("turn on wifi")) return JarvisIntent.ToggleWifi("on")
        if (t.contains("wifi off") || t.contains("turn off wifi")) return JarvisIntent.ToggleWifi("off")

        if (t.contains("bluetooth on")) return JarvisIntent.ToggleBluetooth("on")
        if (t.contains("bluetooth off")) return JarvisIntent.ToggleBluetooth("off")

        if (t.contains("volume up") || t.contains("volume badhao")) return JarvisIntent.SetVolume(80)
        if (t.contains("volume down") || t.contains("volume kam")) return JarvisIntent.SetVolume(30)

        if (t.startsWith("open ") || t.endsWith(" kholo")) {
            val app = t.replace("open ", "").replace(" kholo", "").strip()
            return JarvisIntent.OpenApp(app)
        }

        if (t.contains("read screen") || t.contains("screen padho")) return JarvisIntent.ReadScreen()

        if (t.startsWith("call ")) {
            val contact = t.substring(5).strip()
            return JarvisIntent.CallContact(contact)
        }

        return JarvisIntent.Unknown(rawText)
    }
}
