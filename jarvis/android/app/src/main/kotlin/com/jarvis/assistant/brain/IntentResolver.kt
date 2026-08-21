package com.jarvis.assistant.brain

sealed class JarvisIntent {
    data class GetTime(val raw: String) : JarvisIntent()
    data class GetBattery(val raw: String) : JarvisIntent()
    data class GetStorage(val raw: String = "") : JarvisIntent()
    data class GetCallLog(val raw: String = "") : JarvisIntent()
    data class OpenGallery(val raw: String = "") : JarvisIntent()
    data class ListApps(val category: String? = null) : JarvisIntent()
    data class OpenSettings(val section: String? = null) : JarvisIntent()
    data class ToggleTorch(val state: String) : JarvisIntent()
    data class ToggleWifi(val state: String) : JarvisIntent()
    data class ToggleBluetooth(val state: String) : JarvisIntent()
    data class SetVolume(val level: Int) : JarvisIntent()
    data class MediaControl(val action: String) : JarvisIntent()
    data class OpenApp(val appName: String) : JarvisIntent()
    data class ReadScreen(val target: String = "screen") : JarvisIntent()
    data class CallContact(val contactName: String) : JarvisIntent()
    data class SendSms(val recipient: String, val message: String) : JarvisIntent()
    data class SendWhatsApp(val contactName: String, val message: String) : JarvisIntent()
    data class LocalConversational(val answer: String) : JarvisIntent()
    data class Unknown(val raw: String) : JarvisIntent()
}

class IntentResolver {
    fun resolve(rawText: String): JarvisIntent {
        val t = rawText.lowercase().trim()

        // Conversational & Assistant Basics (0ms instant response)
        if (t in listOf("hello", "hi", "hey", "hey jarvis", "namaste", "suno", "hello jarvis", "suno jarvis", "ji jarvis")) {
            return JarvisIntent.LocalConversational("Hello! I am Jarvis. How can I help you?")
        }
        if (t.contains("who are you") || t.contains("kaun ho") || t.contains("what is your name") || t.contains("tumhara naam")) {
            return JarvisIntent.LocalConversational("I am Jarvis, your personal AI voice and device automation assistant.")
        }
        if (t.contains("how are you") || t.contains("kaise ho") || t.contains("kya haal")) {
            return JarvisIntent.LocalConversational("All systems are operational and ready for your command!")
        }
        if (t.contains("what can you do") || t.contains("kya kar sakte ho") || t.contains("help") || t == "commands") {
            return JarvisIntent.LocalConversational("I can control Flashlight, Wi-Fi, Bluetooth, open apps, check battery & storage, read screen, manage WhatsApp & calls, and assist you with daily tasks.")
        }
        if (t.contains("thank you") || t.contains("thanks") || t.contains("dhanyawad") || t.contains("shukriya")) {
            return JarvisIntent.LocalConversational("You're welcome! Always here to assist you.")
        }
        if (t.contains("bye") || t.contains("alvida") || t.contains("good night") || t.contains("shubh ratri")) {
            return JarvisIntent.LocalConversational("Goodbye! Have a wonderful time ahead.")
        }

        if (t.contains("time") || t.contains("samay") || t.contains("kitne baje")) return JarvisIntent.GetTime(rawText)
        if (t.contains("battery") || t.contains("charge") || t.contains("charging")) return JarvisIntent.GetBattery(rawText)
        if (t.contains("storage") || t.contains("disk space") || t.contains("phone memory")) return JarvisIntent.GetStorage(rawText)
        if (t.contains("call log") || t.contains("recent calls") || t.contains("call history") || t.contains("kiski call aayi")) return JarvisIntent.GetCallLog(rawText)

        // Gallery
        if (t.contains("open gallery") || t.contains("gallery kholo") || t.contains("show photos") || t.contains("photo dikhao")) return JarvisIntent.OpenGallery(rawText)

        // App Discovery / Listing
        if (t.contains("music apps")) return JarvisIntent.ListApps("music")
        if (t.contains("social apps")) return JarvisIntent.ListApps("social")
        if (t.contains("ai apps") || t.contains("assistant apps")) return JarvisIntent.ListApps("ai")
        if (t.contains("game apps") || t.contains("games list")) return JarvisIntent.ListApps("games")
        if (t.contains("apps list") || t.contains("all apps") || t.contains("list apps") || t.contains("installed apps")) return JarvisIntent.ListApps()

        // Torch / Flashlight
        if (t.contains("torch on") || t.contains("flashlight on") || t.contains("torch chalo") || t.contains("light on") || t.contains("torch jalao")) return JarvisIntent.ToggleTorch("on")
        if (t.contains("torch off") || t.contains("flashlight off") || t.contains("torch band") || t.contains("light off") || t.contains("torch bujhao")) return JarvisIntent.ToggleTorch("off")

        // WiFi
        if (t.contains("wifi on") || t.contains("turn on wifi") || t.contains("wifi chalo") || t.contains("wifi chalu")) return JarvisIntent.ToggleWifi("on")
        if (t.contains("wifi off") || t.contains("turn off wifi") || t.contains("wifi band")) return JarvisIntent.ToggleWifi("off")

        // Bluetooth
        if (t.contains("bluetooth on") || t.contains("bluetooth chalo") || t.contains("bluetooth chalu")) return JarvisIntent.ToggleBluetooth("on")
        if (t.contains("bluetooth off") || t.contains("bluetooth band")) return JarvisIntent.ToggleBluetooth("off")

        // Media / Music
        if (t.contains("play music") || t.contains("music play") || t.contains("gaana bajao") || t.contains("gana bajao") || t.contains("song play")) return JarvisIntent.MediaControl("play")
        if (t.contains("pause music") || t.contains("music pause") || t.contains("gaana roko") || t.contains("gana roko") || t.contains("song pause") || t.contains("stop music")) return JarvisIntent.MediaControl("pause")
        if (t.contains("next song") || t.contains("next track") || t.contains("agla gaana") || t.contains("music next")) return JarvisIntent.MediaControl("next")
        if (t.contains("previous song") || t.contains("prev song") || t.contains("pichhla gaana") || t.contains("music prev")) return JarvisIntent.MediaControl("prev")

        // Volume
        if (t.contains("volume up") || t.contains("volume badhao") || t.contains("awaz badhao") || t.contains("volume tez")) return JarvisIntent.SetVolume(80)
        if (t.contains("volume down") || t.contains("volume kam") || t.contains("awaz kam") || t.contains("volume dheere")) return JarvisIntent.SetVolume(30)
        if (t.contains("mute") || t.contains("silent") || t.contains("chup")) return JarvisIntent.SetVolume(0)

        // Settings
        if (t.contains("open settings") || t.contains("settings kholo") || t == "settings") return JarvisIntent.OpenSettings()

        // WhatsApp
        if (t.contains("whatsapp")) {
            val targetRaw = rawText.substring(t.indexOf("whatsapp") + "whatsapp".length)
            var words = targetRaw.trim().split(" ").filter { it.isNotBlank() }
            if (words.firstOrNull()?.equals("to", ignoreCase = true) == true) {
                words = words.drop(1)
            }
            val contact = words.firstOrNull() ?: "contact"
            val msg = if (words.size > 1) words.subList(1, words.size).joinToString(" ") else "Hello"
            return JarvisIntent.SendWhatsApp(contact, msg)
        }

        // SMS
        if (t.startsWith("sms ") || t.contains("send sms")) {
            val msg = t.replace("send sms", "").replace("sms", "").trim()
            return JarvisIntent.SendSms("contact", msg)
        }

        // Open App
        if (t.startsWith("open ") || t.endsWith(" kholo") || t.startsWith("kholo ") || t.contains("launch ")) {
            val app = t.replace("open ", "").replace(" kholo", "").replace("kholo ", "").replace("launch ", "").trim()
            return JarvisIntent.OpenApp(app)
        }

        // Read Screen
        if (t.contains("read screen") || t.contains("screen padho") || t.contains("screen dekho")) return JarvisIntent.ReadScreen()

        // Call
        if (t.startsWith("call ") || t.startsWith("phone ") || t.contains("ko call karo")) {
            val contact = rawText.trim().replace(Regex("(?i)^(call|phone)\\s+"), "").replace(Regex("(?i)\\s+ko call karo$"), "").trim()
            return JarvisIntent.CallContact(contact)
        }

        return JarvisIntent.Unknown(rawText)
    }
}
