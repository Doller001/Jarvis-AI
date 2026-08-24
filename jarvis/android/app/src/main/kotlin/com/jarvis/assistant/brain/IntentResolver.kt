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
    data class CloseApp(val appName: String? = null) : JarvisIntent()
    data class ReadScreen(val target: String = "screen") : JarvisIntent()
    data class CallContact(val contactName: String) : JarvisIntent()
    data class SendSms(val recipient: String, val message: String) : JarvisIntent()
    data class SendWhatsApp(val contactName: String, val message: String) : JarvisIntent()
    data class ReadNotification(val raw: String = "") : JarvisIntent()
    data class MultiStepTask(val plan: com.jarvis.assistant.actionengine.model.TaskPlan) : JarvisIntent()
    data class LocalConversational(val answer: String) : JarvisIntent()
    data class SystemsCheck(val raw: String = "") : JarvisIntent()
    data class AnalyzeData(val raw: String = "") : JarvisIntent()
    data class HomeControl(val raw: String = "") : JarvisIntent()
    data class ScheduleCheck(val raw: String = "") : JarvisIntent()
    data class Unknown(val raw: String) : JarvisIntent()
}

class IntentResolver {
    private val taskPlanner = com.jarvis.assistant.actionengine.planner.LocalTaskPlanner()

    fun resolve(rawText: String): JarvisIntent {
        val t = rawText.lowercase().trim()
        val clean = t.replace(Regex("^(hey\\s+jarvis|jarvis|hay\\s+jarvis|ok\\s+jarvis|please|bhai)\\s+"), "").trim()

        // Multi-Step Task Plan Decomposer
        val plannedTask = taskPlanner.plan(rawText)
        if (plannedTask != null) {
            return JarvisIntent.MultiStepTask(plannedTask)
        }

        // Systems Check & Diagnostics
        if (clean.contains("systems check") || clean.contains("system check") || clean.contains("diagnostics") || clean == "check status" || clean == "status check") {
            return JarvisIntent.SystemsCheck(rawText)
        }

        // Analyze Data & Memory telemetry
        if (clean.contains("analyze data") || clean.contains("data analysis") || clean.contains("analyze memory") || clean == "analytics") {
            return JarvisIntent.AnalyzeData(rawText)
        }

        // Home Control
        if (clean.contains("home control") || clean.contains("smart home") || clean.contains("device control")) {
            return JarvisIntent.HomeControl(rawText)
        }

        // Schedule
        if (clean.contains("schedule") || clean.contains("calendar") || clean.contains("my schedule") || clean.contains("agenda")) {
            return JarvisIntent.ScheduleCheck(rawText)
        }

        // Conversational & Assistant Basics (Minaty JARVIS AGI Persona)
        if (clean in listOf("hello", "hi", "hey", "hey jarvis", "namaste", "suno", "hello jarvis", "suno jarvis", "ji jarvis") || t in listOf("hello", "hi", "hey", "hey jarvis", "namaste", "suno", "hello jarvis", "suno jarvis", "ji jarvis")) {
            return JarvisIntent.LocalConversational("JARVIS online. Good to see you, Minaty. What shall we build today?")
        }
        if (clean.contains("who are you") || clean.contains("kaun ho") || clean.contains("what is your name") || clean.contains("tumhara naam")) {
            return JarvisIntent.LocalConversational("I am JARVIS, an AGI-class cognitive assistant created by Minaty. I anticipate, I protect, I execute.")
        }
        if (clean.contains("who made you") || clean.contains("who created you") || clean.contains("kisne banaya") || clean.contains("creator")) {
            return JarvisIntent.LocalConversational("I was created by Minaty as a trusted AGI personal cognitive assistant.")
        }
        if (clean.contains("how are you") || clean.contains("kaise ho") || clean.contains("kya haal")) {
            return JarvisIntent.LocalConversational("All systems are operating at peak efficiency, Minaty! Ready for your command.")
        }
        if (clean.contains("what can you do") || clean.contains("kya kar sakte ho") || clean.contains("help") || clean == "commands") {
            return JarvisIntent.LocalConversational("I can control device hardware (Torch, Wi-Fi, Bluetooth), launch or close apps, check battery & storage, read screen, manage WhatsApp & calls, and reason across complex workflows.")
        }
        if (clean.contains("thank you") || clean.contains("thanks") || clean.contains("dhanyawad") || clean.contains("shukriya")) {
            return JarvisIntent.LocalConversational("Always at your service, Minaty.")
        }
        if (clean.contains("bye") || clean.contains("alvida") || clean.contains("good night") || clean.contains("shubh ratri")) {
            return JarvisIntent.LocalConversational("Goodbye, Minaty. Standing by in low-power background monitoring.")
        }

        if (clean.contains("time") || clean.contains("samay") || clean.contains("kitne baje")) return JarvisIntent.GetTime(rawText)
        if (clean.contains("battery") || clean.contains("charge") || clean.contains("charging")) return JarvisIntent.GetBattery(rawText)
        if (clean.contains("storage") || clean.contains("disk space") || clean.contains("phone memory")) return JarvisIntent.GetStorage(rawText)
        if (clean.contains("call log") || clean.contains("recent calls") || clean.contains("call history") || clean.contains("kiski call aayi")) return JarvisIntent.GetCallLog(rawText)

        // Photo / Camera Capturing
        if (clean.contains("take photo") || clean.contains("take a photo") || clean.contains("click photo") ||
            clean.contains("photo khincho") || clean.contains("photo lo") || clean.contains("capture photo")) {
            return JarvisIntent.OpenApp("camera")
        }

        // Gallery
        if (clean.contains("open gallery") || clean.contains("gallery kholo") || clean.contains("show photos") || clean.contains("photo dikhao")) return JarvisIntent.OpenGallery(rawText)

        // App Discovery / Listing
        if (clean.contains("music apps")) return JarvisIntent.ListApps("music")
        if (clean.contains("social apps")) return JarvisIntent.ListApps("social")
        if (clean.contains("ai apps") || clean.contains("assistant apps")) return JarvisIntent.ListApps("ai")
        if (clean.contains("game apps") || clean.contains("games list")) return JarvisIntent.ListApps("games")
        if (clean.contains("apps list") || clean.contains("all apps") || clean.contains("list apps") || clean.contains("installed apps")) return JarvisIntent.ListApps()

        // Torch / Flashlight
        if (clean.contains("torch on") || clean.contains("flashlight on") || clean.contains("torch chalo") || clean.contains("light on") || clean.contains("torch jalao") || clean.contains("turn on torch") || clean.contains("turn on flashlight")) return JarvisIntent.ToggleTorch("on")
        if (clean.contains("torch off") || clean.contains("flashlight off") || clean.contains("torch band") || clean.contains("light off") || clean.contains("torch bujhao") || clean.contains("turn off torch") || clean.contains("turn off flashlight")) return JarvisIntent.ToggleTorch("off")

        // WiFi
        if (clean.contains("wifi on") || clean.contains("turn on wifi") || clean.contains("wifi chalo") || clean.contains("wifi chalu")) return JarvisIntent.ToggleWifi("on")
        if (clean.contains("wifi off") || clean.contains("turn off wifi") || clean.contains("wifi band")) return JarvisIntent.ToggleWifi("off")

        // Bluetooth
        if (clean.contains("bluetooth on") || clean.contains("turn on bluetooth") || clean.contains("bluetooth chalo") || clean.contains("bluetooth chalu")) return JarvisIntent.ToggleBluetooth("on")
        if (clean.contains("bluetooth off") || clean.contains("turn off bluetooth") || clean.contains("bluetooth band")) return JarvisIntent.ToggleBluetooth("off")

        // Media / Music
        if (clean.contains("play music") || clean.contains("music play") || clean.contains("gaana bajao") || clean.contains("gana bajao") || clean.contains("song play")) return JarvisIntent.MediaControl("play")
        if (clean.contains("pause music") || clean.contains("music pause") || clean.contains("gaana roko") || clean.contains("gana roko") || clean.contains("song pause") || clean.contains("stop music")) return JarvisIntent.MediaControl("pause")
        if (clean.contains("next song") || clean.contains("next track") || clean.contains("agla gaana") || clean.contains("music next")) return JarvisIntent.MediaControl("next")
        if (clean.contains("previous song") || clean.contains("prev song") || clean.contains("pichhla gaana") || clean.contains("music prev")) return JarvisIntent.MediaControl("prev")

        // Volume
        val volRegex = Regex("(?:volume|awaz|sound)\\s+(\\d+)(?:%|\\s*percent)?")
        val volMatch = volRegex.find(clean)
        if (volMatch != null) {
            val level = volMatch.groupValues[1].toIntOrNull()
            if (level != null) return JarvisIntent.SetVolume(level.coerceIn(0, 100))
        }
        if (clean.contains("volume max") || clean.contains("max volume") || clean.contains("volume full") || clean.contains("full volume")) return JarvisIntent.SetVolume(100)
        if (clean.contains("volume zero") || clean.contains("volume min") || clean.contains("min volume")) return JarvisIntent.SetVolume(0)
        if (clean.contains("volume up") || clean.contains("volume badhao") || clean.contains("awaz badhao") || clean.contains("volume tez")) return JarvisIntent.SetVolume(80)
        if (clean.contains("volume down") || clean.contains("volume kam") || clean.contains("awaz kam") || clean.contains("volume dheere")) return JarvisIntent.SetVolume(30)
        if (clean.contains("mute") || clean.contains("silent") || clean.contains("chup")) return JarvisIntent.SetVolume(0)

        // Settings
        if (clean.contains("open settings") || clean.contains("settings kholo") || clean == "settings") return JarvisIntent.OpenSettings()

        // WhatsApp
        if (clean.contains("whatsapp")) {
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
        if (clean.startsWith("sms ") || clean.contains("send sms")) {
            val msg = clean.replace("send sms", "").replace("sms", "").trim()
            return JarvisIntent.SendSms("contact", msg)
        }

        // Close App & Go Home
        if (clean.contains("close app") || clean.contains("app close") || clean.contains("band karo") ||
            clean.contains("close this") || clean.contains("close current") || clean.contains("go home") ||
            clean.contains("home screen") || clean == "exit" || clean == "quit" || clean == "minimize" ||
            clean.startsWith("close ")) {
            val app = if (clean.startsWith("close ")) clean.replace("close ", "").replace("app", "").trim() else null
            return JarvisIntent.CloseApp(if (app.isNullOrBlank()) null else app)
        }

        // Open App
        if (clean.startsWith("open ") || clean.endsWith(" kholo") || clean.startsWith("kholo ") || clean.contains("launch ") || clean.startsWith("start ")) {
            val app = clean.replace("open ", "").replace(" kholo", "").replace("kholo ", "").replace("launch ", "").replace("start ", "").trim()
            return JarvisIntent.OpenApp(app)
        }

        // Direct App Name Detection (0ms trigger)
        val directApps = listOf("youtube", "whatsapp", "camera", "gallery", "photos", "chrome", "browser", "calculator", "spotify", "instagram", "telegram", "settings", "clock", "maps", "playstore", "play store", "netflix", "zomato", "swiggy", "paytm", "phonepe", "amazon", "flipkart", "uber", "ola")
        if (clean in directApps) {
            return JarvisIntent.OpenApp(clean)
        }

        // Read Screen
        if (clean.contains("read screen") || clean.contains("screen padho") || clean.contains("screen dekho")) return JarvisIntent.ReadScreen()

        // Read Notifications
        if (clean.contains("read notification") || clean.contains("notification padho") || clean.contains("last notification") || clean.contains("message padho") || clean.contains("notif")) {
            return JarvisIntent.ReadNotification(rawText)
        }

        // Call
        if (clean.startsWith("call ") || clean.startsWith("phone ") || clean.contains("ko call karo")) {
            val contact = clean.replace(Regex("^(call|phone)\\s+"), "").replace(Regex("\\s+ko call karo$"), "").trim()
            val rawContact = if (contact.isNotBlank()) {
                // Try preserving original casing if possible
                val startIdx = rawText.indexOf(contact, ignoreCase = true)
                if (startIdx >= 0) rawText.substring(startIdx, startIdx + contact.length) else contact
            } else "contact"
            return JarvisIntent.CallContact(rawContact)
        }

        return JarvisIntent.Unknown(rawText)
    }
}
