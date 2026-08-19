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
    data class SendWhatsApp(val contactName: String, val message: String) : JarvisIntent()
    data class Unknown(val raw: String) : JarvisIntent()
}

class IntentResolver {
    fun resolve(rawText: String): JarvisIntent {
        val t = rawText.lowercase().trim()

        if (t.contains("time") || t.contains("samay")) return JarvisIntent.GetTime(rawText)
        if (t.contains("battery")) return JarvisIntent.GetBattery(rawText)

        if (t.contains("torch on") || t.contains("flashlight on") || t.contains("torch chalo")) return JarvisIntent.ToggleTorch("on")
        if (t.contains("torch off") || t.contains("flashlight off") || t.contains("torch band")) return JarvisIntent.ToggleTorch("off")

        if (t.contains("wifi on") || t.contains("turn on wifi") || t.contains("wifi chalo")) return JarvisIntent.ToggleWifi("on")
        if (t.contains("wifi off") || t.contains("turn off wifi") || t.contains("wifi band")) return JarvisIntent.ToggleWifi("off")

        if (t.contains("bluetooth on") || t.contains("bluetooth chalo")) return JarvisIntent.ToggleBluetooth("on")
        if (t.contains("bluetooth off") || t.contains("bluetooth band")) return JarvisIntent.ToggleBluetooth("off")

        if (t.contains("volume up") || t.contains("volume badhao")) return JarvisIntent.SetVolume(80)
        if (t.contains("volume down") || t.contains("volume kam")) return JarvisIntent.SetVolume(30)

        if (t.contains("whatsapp")) {
            val parts = t.split("whatsapp")
            val target = parts.getOrNull(1)?.trim() ?: ""
            var words = target.split(" ").filter { it.isNotBlank() }
            if (words.firstOrNull()?.equals("to", ignoreCase = true) == true) {
                words = words.drop(1)
            }
            val contact = words.firstOrNull() ?: "contact"
            val msg = if (words.size > 1) words.subList(1, words.size).joinToString(" ") else "Hello"
            return JarvisIntent.SendWhatsApp(contact, msg)
        }

        if (t.startsWith("sms ") || t.contains("send sms")) {
            val msg = t.replace("send sms", "").replace("sms", "").trim()
            return JarvisIntent.SendSms("contact", msg)
        }

        if (t.startsWith("open ") || t.endsWith(" kholo")) {
            val app = t.replace("open ", "").replace(" kholo", "").trim()
            return JarvisIntent.OpenApp(app)
        }

        if (t.contains("read screen") || t.contains("screen padho")) return JarvisIntent.ReadScreen()

        if (t.startsWith("call ")) {
            val contact = t.substring(5).trim()
            return JarvisIntent.CallContact(contact)
        }

        return JarvisIntent.Unknown(rawText)
    }
}
