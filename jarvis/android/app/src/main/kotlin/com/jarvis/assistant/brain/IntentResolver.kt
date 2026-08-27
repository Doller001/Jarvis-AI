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
    data class PlayMediaSearch(val query: String, val app: String = "youtube") : JarvisIntent()
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

    // ===== NEW: Display & Screen =====
    data class SetBrightness(val level: Int) : JarvisIntent()
    data class ToggleRotationLock(val state: String) : JarvisIntent()
    data class SetRingerMode(val mode: String) : JarvisIntent() // silent / vibrate / normal
    data class ToggleDnd(val state: String) : JarvisIntent() // on / off
    data class TakeScreenshot(val raw: String = "") : JarvisIntent()

    // ===== NEW: Power & Battery =====
    data class BatteryChargingStatus(val raw: String = "") : JarvisIntent()

    // ===== NEW: Connectivity =====
    data class ConnectBluetooth(val deviceName: String = "") : JarvisIntent()

    // ===== NEW: Storage & Clipboard =====
    data class CopyToClipboard(val text: String) : JarvisIntent()
    data class ReadClipboard(val raw: String = "") : JarvisIntent()

    // ===== NEW: Time / Productivity =====
    data class SetAlarm(val time: String, val label: String = "") : JarvisIntent()
    data class SetReminder(val delayMinutes: Int, val message: String) : JarvisIntent()
    data class SetTimer(val seconds: Int) : JarvisIntent()
    data class ReadCalendar(val raw: String = "") : JarvisIntent()

    // ===== NEW: Location & Web =====
    data class GetLocation(val raw: String = "") : JarvisIntent()
    data class GetWeather(val raw: String = "") : JarvisIntent()
    data class WebSearch(val query: String) : JarvisIntent()
    data class NavigateTo(val place: String) : JarvisIntent()

    // ===== NEW: Security =====
    data class LockScreen(val raw: String = "") : JarvisIntent()
    data class EmergencySos(val raw: String = "") : JarvisIntent()

    // ===== NEW: Comms proactive =====
    data class ReadSmsInbox(val raw: String = "") : JarvisIntent()
    data class ReadWhatsAppUnread(val raw: String = "") : JarvisIntent()

    // ===== NEW: Memory control =====
    data class ForgetMemory(val raw: String = "") : JarvisIntent()
    data class ExportLogs(val raw: String = "") : JarvisIntent()

    // ===== NEW: Routine Engine =====
    data class RunRoutine(val routineName: String) : JarvisIntent()
    data class ListRoutines(val raw: String = "") : JarvisIntent()

    // ===== NEW: System utilities =====
    data class ToggleAirplaneMode(val state: String) : JarvisIntent()
    data class GetDailyBriefing(val raw: String = "") : JarvisIntent()

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
        if (clean.contains("selfie") || clean.contains("take photo") || clean.contains("take a photo") || clean.contains("click photo") ||
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

        // Media / Music Controls & Search
        if (clean == "play music" || clean == "music play" || clean == "gaana bajao" || clean == "gana bajao" || clean == "song play") return JarvisIntent.MediaControl("play")
        if (clean.contains("pause music") || clean.contains("music pause") || clean.contains("gaana roko") || clean.contains("gana roko") || clean.contains("song pause") || clean.contains("stop music")) return JarvisIntent.MediaControl("pause")
        if (clean.contains("next song") || clean.contains("next track") || clean.contains("agla gaana") || clean.contains("music next")) return JarvisIntent.MediaControl("next")
        if (clean.contains("previous song") || clean.contains("prev song") || clean.contains("pichhla gaana") || clean.contains("music prev")) return JarvisIntent.MediaControl("prev")

        // Specific Song / Media Search (e.g., "play headlight song", "play believer", "open youtube and play ...")
        if (clean.startsWith("play ") || clean.contains("gaana bajao") || clean.contains("gana bajao") || clean.contains("baja do") || (clean.startsWith("open youtube and play "))) {
            val query = clean
                .replace(Regex("^(?:open\\s+(?:youtube|spotify)\\s+and\\s+)?play\\s+"), "")
                .replace(Regex("\\b(gaana bajao|gana bajao|baja do|song|video|track)\\b"), "")
                .replace(Regex("\\b(on\\s+youtube|on\\s+spotify)\\b"), "")
                .trim()
            if (query.isNotBlank() && query != "music") {
                val app = if (clean.contains("spotify")) "spotify" else "youtube"
                return JarvisIntent.PlayMediaSearch(query, app)
            }
        }

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

        // Settings — section-aware
        if (clean.contains("open settings") || clean.contains("settings kholo") || clean == "settings") return JarvisIntent.OpenSettings()
        if (clean.contains("location settings") || clean.contains("gps settings")) return JarvisIntent.OpenSettings("location")
        if (clean.contains("security settings") || clean.contains("screen lock settings")) return JarvisIntent.OpenSettings("security")
        if (clean.contains("nfc settings")) return JarvisIntent.OpenSettings("nfc")
        if (clean.contains("storage settings") || clean.contains("device storage")) return JarvisIntent.OpenSettings("storage")

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
            val contact = clean.replace(Regex("^(call|phone)\s+"), "").replace(Regex("\s+ko call karo$"), "").trim()
            val rawContact = if (contact.isNotBlank()) {
                // Try preserving original casing if possible
                val startIdx = rawText.indexOf(contact, ignoreCase = true)
                if (startIdx >= 0) rawText.substring(startIdx, startIdx + contact.length) else contact
            } else "contact"
            return JarvisIntent.CallContact(rawContact)
        }

        // ===== NEW INTENT RULES =====

        // Display: Brightness
        val brightRegex = Regex("(?:brightness|screen light|screen brightness|roshni)\s+(?:set\s+)?(?:to\s+)?(\d+)\s*%?")
        val brightMatch = brightRegex.find(clean)
        if (brightMatch != null) {
            val lvl = brightMatch.groupValues[1].toIntOrNull()?.coerceIn(0, 100) ?: 50
            return JarvisIntent.SetBrightness(lvl)
        }
        if (clean.contains("brightness badhao") || clean.contains("brightness up") || clean.contains("screen bright") || clean.contains("screen roshni badhao")) return JarvisIntent.SetBrightness(80)
        if (clean.contains("brightness kam") || clean.contains("brightness down") || clean.contains("screen dim") || clean.contains("screen dheema") || clean.contains("screen roshni kam")) return JarvisIntent.SetBrightness(20)
        if (clean.contains("brightness full") || clean.contains("screen full bright")) return JarvisIntent.SetBrightness(100)
        if (clean.contains("brightness half") || clean.contains("screen aadhi")) return JarvisIntent.SetBrightness(50)

        // Display: Rotation lock
        if (clean.contains("rotation lock") || clean.contains("screen rotate lock") || clean.contains("rotate band")) {
            return JarvisIntent.ToggleRotationLock(if (clean.contains("off") || clean.contains("band") || clean.contains("kholo")) "off" else "on")
        }
        if (clean.contains("auto rotate on") || clean.contains("rotation on") || clean.contains("ghoomne do")) return JarvisIntent.ToggleRotationLock("off")
        if (clean.contains("auto rotate off") || clean.contains("rotation lock on") || clean.contains("ghoomna band")) return JarvisIntent.ToggleRotationLock("on")

        // Audio: Ringer mode
        if (clean.contains("silent mode") || clean.contains("silent karo") || clean.contains("chup mode") || clean.contains("silent on")) return JarvisIntent.SetRingerMode("silent")
        if (clean.contains("vibrate mode") || clean.contains("vibrate karo") || clean.contains("kampan mode")) return JarvisIntent.SetRingerMode("vibrate")
        if (clean.contains("normal mode") || clean.contains("ring mode") || clean.contains("normal sound")) return JarvisIntent.SetRingerMode("normal")
        if (clean.contains("silent off") || clean.contains("unmute ringer")) return JarvisIntent.SetRingerMode("normal")

        // Audio: DND
        if (clean.contains("do not disturb") || clean.contains("dnd on") || clean.contains("disturb mat karo") || clean.contains("dnd chalu")) return JarvisIntent.ToggleDnd("on")
        if (clean.contains("dnd off") || clean.contains("dnd band") || clean.contains("disturb allow")) return JarvisIntent.ToggleDnd("off")

        // Screen: Screenshot
        if (clean.contains("screenshot") || clean.contains("screen shot") || clean.contains("screen capture") || clean.contains("screen ki photo")) return JarvisIntent.TakeScreenshot(rawText)

        // Battery charging status
        if (clean.contains("charging") || clean.contains("charge ho raha") || clean.contains("charger lagа") || clean.contains("plugged in") || clean.contains("battery status")) return JarvisIntent.BatteryChargingStatus(rawText)
        if (clean.contains("battery health") || clean.contains("battery temp") || clean.contains("battery garam")) return JarvisIntent.BatteryChargingStatus(rawText)

        // Connectivity: Bluetooth connect
        if (clean.contains("bluetooth connect") || clean.contains("bt connect") || clean.contains("bluetooth se connect") || clean.contains("earbuds connect")) {
            val dev = clean.replace(Regex(".*(?:bluetooth connect|bt connect|bluetooth se connect|earbuds connect)\s*"), "").trim()
            return JarvisIntent.ConnectBluetooth(dev)
        }

        // Clipboard
        if (clean.contains("copy to clipboard") || clean.contains("clipboard me copy") || clean.contains("copy karo")) {
            val txt = clean.replace("copy to clipboard", "").replace("clipboard me copy", "").replace("copy karo", "").replace("copy", "").trim()
            return JarvisIntent.CopyToClipboard(txt.ifBlank { rawText })
        }
        if (clean.contains("read clipboard") || clean.contains("clipboard padho") || clean.contains("clipboard me kya hai")) return JarvisIntent.ReadClipboard(rawText)

        // Time: Alarm
        val alarmRegex = Regex("(?:alarm|alarma|alarm lagao|alarm set)\s+(?:at\s+)?(\d{1,2})\s*(?:baje|am|pm|o'?clock)?")
        val alarmMatch = alarmRegex.find(clean)
        if (clean.contains("alarm") && alarmMatch != null) {
            return JarvisIntent.SetAlarm(alarmMatch.groupValues[1], "Jarvis Alarm")
        }
        if (clean.contains("alarm lagao") || clean.contains("alarm set") || clean.contains("alarm laga") || clean.startsWith("alarm ")) {
            val t = clean.replace("alarm lagao", "").replace("alarm set", "").replace("alarm laga", "").replace("alarm", "").trim()
            return JarvisIntent.SetAlarm(t.ifBlank { "7" }, "Jarvis Alarm")
        }

        // Time: Reminder
        val remindRegex = Regex("(?:remind|yaad|reminder)\s+(?:me\s+)?(?:in\s+)?(\d+)\s*(?:minute|min|minutes|ghante|hour|hours)?.+")
        val remindMatch = remindRegex.find(clean)
        if (clean.contains("remind") || clean.contains("yaad dilao") || clean.contains("reminder")) {
            val numRegex = Regex("(\d+)\s*(minute|min|minutes|ghante|ghanta|hour|hours)")
            val nm = numRegex.find(clean)
            val mins = when {
                nm == null -> 60
                nm.groupValues[2].startsWith("gha") -> nm.groupValues[1].toIntOrNull()?.times(60) ?: 60
                nm.groupValues[2].startsWith("hour") -> nm.groupValues[1].toIntOrNull()?.times(60) ?: 60
                else -> nm.groupValues[1].toIntOrNull() ?: 60
            }
            val msg = clean.replace(Regex(".*(?:remind|yaad dilao|reminder)\s+(?:me\s+)?(?:in\s+)?(?:to\s+)?" + Regex.escape(nm?.value ?: "")), "")
                .replace(Regex("(?:remind|yaad dilao|reminder|me|in|to|\d+\s*(?:minute|min|minutes|ghante|ghanta|hour|hours))"), "").trim()
            return JarvisIntent.SetReminder(mins, msg.ifBlank { "Reminder" })
        }

        // Time: Timer
        val timerRegex = Regex("(?:timer|countdown)\s+(?:for\s+)?(\d+)\s*(minute|min|minutes|second|sec|seconds|ghante|hour|hours)?")
        val timerMatch = timerRegex.find(clean)
        if (clean.contains("timer") || clean.contains("countdown") || clean.contains("timer lagao")) {
            val tm = timerRegex.find(clean)
            val secs = if (tm != null) {
                val n = tm.groupValues[1].toIntOrNull() ?: 60
                when {
                    tm.groupValues[2].startsWith("gha") || tm.groupValues[2].startsWith("hour") -> n * 3600
                    tm.groupValues[2].startsWith("min") -> n * 60
                    tm.groupValues[2].startsWith("sec") -> n
                    else -> n * 60
                }
            } else 60
            return JarvisIntent.SetTimer(secs)
        }

        // Calendar
        if (clean.contains("calendar") || clean.contains("my schedule") || clean.contains("aaj ka schedule") || clean.contains("meetings") || clean.contains("appointments") || clean.contains("calendar dikhao") || clean.contains("kal ka kya hai")) return JarvisIntent.ReadCalendar(rawText)

        // Location & Weather
        if (clean.contains("where am i") || clean.contains("my location") || clean.contains("main kahan hoon") || clean.contains("location batao")) return JarvisIntent.GetLocation(rawText)
        if (clean.contains("weather") || clean.contains("mausam") || clean.contains("temperature") || clean.contains("kitni garmi") || clean.contains("barish")) return JarvisIntent.GetWeather(rawText)
        if (clean.contains("navigate") || clean.contains("navigation") || clean.contains("route to") || clean.contains("jaane ka rasta") || clean.startsWith("navigate ")) {
            val place = clean.replace("navigate", "").replace("navigation", "").replace("route to", "").replace("jaane ka rasta", "").replace("to", "").trim()
            return JarvisIntent.NavigateTo(place.ifBlank { "home" })
        }

        // Web search
        if (clean.contains("search web") || clean.contains("web search") || clean.contains("google pe") || clean.contains("google search") || clean.contains("dhoondo") || clean.contains("search karo") || clean.startsWith("search ")) {
            val q = clean.replace("search web", "").replace("web search", "").replace("google pe", "").replace("google search", "").replace("dhoondo", "").replace("search karo", "").replace("search", "").trim()
            if (q.isNotBlank() && q != "the") return JarvisIntent.WebSearch(q)
        }

        // Security
        if (clean.contains("lock screen") || clean.contains("screen lock") || clean.contains("phone lock") || clean.contains("lock my phone") || clean.contains("phone lock karo")) return JarvisIntent.LockScreen(rawText)
        if (clean.contains("emergency") || clean.contains("sos") || clean.contains("help me") || clean.contains("panic") || clean.contains("madad")) return JarvisIntent.EmergencySos(rawText)

        // Comms proactive
        if (clean.contains("read sms") || clean.contains("sms padho") || clean.contains("inbox padho") || clean.contains("messages padho") || clean.contains("sms inbox") || clean.contains("message dikhao")) return JarvisIntent.ReadSmsInbox(rawText)
        if (clean.contains("whatsapp unread") || clean.contains("whatsapp message padho") || clean.contains("unread whatsapp") || clean.contains("whatsapp unread padho")) return JarvisIntent.ReadWhatsAppUnread(rawText)

        // Memory
        if (clean.contains("forget everything") || clean.contains("clear memory") || clean.contains("memory delete") || clean.contains("sab bhool jao") || clean.contains("memory clear")) return JarvisIntent.ForgetMemory(rawText)
        if (clean.contains("export logs") || clean.contains("export memory") || clean.contains("save logs") || clean.contains("logs bhejo")) return JarvisIntent.ExportLogs(rawText)

        // ===== Airplane mode =====
        if (clean.contains("airplane mode on") || clean.contains("flight mode on") || clean.contains("airplane on") || clean.contains("aeroplane mode on")) return JarvisIntent.ToggleAirplaneMode("on")
        if (clean.contains("airplane mode off") || clean.contains("flight mode off") || clean.contains("airplane off") || clean.contains("aeroplane mode off")) return JarvisIntent.ToggleAirplaneMode("off")

        // ===== Daily Briefing =====
        if (clean.contains("daily briefing") || clean.contains("morning briefing") || clean.contains("good morning briefing") || clean.contains("subah ka briefing") || clean.contains("aaj ka update") || clean.contains("aaj ka summary")) return JarvisIntent.GetDailyBriefing(rawText)

        // ===== Routine Engine =====
        if (clean.contains("routines list") || clean.contains("list routines") || clean.contains("kaunsi routines") || clean.contains("routines dikhao") || clean == "routines") return JarvisIntent.ListRoutines(rawText)
        // Routine trigger keywords — match any known alias
        val routineEngine = com.jarvis.assistant.routines.RoutineEngine(null)
        val resolvedRoutine = routineEngine.resolveRoutineName(clean)
        if (resolvedRoutine != null) return JarvisIntent.RunRoutine(resolvedRoutine)

        return JarvisIntent.Unknown(rawText)
    }
}
