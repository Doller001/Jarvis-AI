package com.jarvis.assistant.brain

sealed class JarvisIntent {
    data class GetTime(val raw: String = "") : JarvisIntent()
    data class GetBattery(val raw: String = "") : JarvisIntent()
    data class GetStorage(val raw: String = "") : JarvisIntent()
    data class GetCallLog(val raw: String = "") : JarvisIntent()
    data class OpenGallery(val raw: String = "") : JarvisIntent()
    data class ListApps(val category: String? = null) : JarvisIntent()
    data class OpenSettings(val section: String? = null) : JarvisIntent()
    data class ToggleTorch(val state: String) : JarvisIntent()
    data class ToggleWifi(val state: String) : JarvisIntent()
    data class ToggleBluetooth(val state: String) : JarvisIntent()
    data class SetVolume(
        val level: Int = 50,
        val isRelative: Boolean = false,
        val directionUp: Boolean = false,
        val isMute: Boolean = false,
        val isUnmute: Boolean = false
    ) : JarvisIntent()
    data class MediaControl(val action: String) : JarvisIntent() // play / pause / stop / next / prev
    data class PlayMediaSearch(val query: String, val app: String = "youtube") : JarvisIntent()
    data class OpenApp(val appName: String) : JarvisIntent()
    data class CloseApp(val appName: String? = null) : JarvisIntent()
    data class ReadScreen(val target: String = "screen") : JarvisIntent()
    data class CallContact(val contactName: String) : JarvisIntent()
    data class GetContact(val contactName: String) : JarvisIntent()
    data class SendSms(val recipient: String, val message: String) : JarvisIntent()
    data class SendWhatsApp(val contactName: String, val message: String) : JarvisIntent()
    data class ReadNotification(val raw: String = "") : JarvisIntent()
    data class MultiStepTask(val plan: com.jarvis.assistant.actionengine.model.TaskPlan) : JarvisIntent()
    data class LocalConversational(val answer: String) : JarvisIntent()
    data class SystemsCheck(val raw: String = "") : JarvisIntent()
    data class AnalyzeData(val raw: String = "") : JarvisIntent()
    data class HomeControl(val raw: String = "") : JarvisIntent()
    data class ScheduleCheck(val raw: String = "") : JarvisIntent()

    // ===== Display & Screen =====
    data class SetBrightness(val level: Int) : JarvisIntent()
    data class ToggleRotationLock(val state: String) : JarvisIntent()
    data class SetRingerMode(val mode: String) : JarvisIntent() // silent / vibrate / normal
    data class ToggleDnd(val state: String) : JarvisIntent() // on / off
    data class TakeScreenshot(val raw: String = "") : JarvisIntent()

    // ===== Power & Battery =====
    data class BatteryChargingStatus(val raw: String = "") : JarvisIntent()

    // ===== Connectivity =====
    data class ConnectBluetooth(val deviceName: String = "") : JarvisIntent()

    // ===== Storage & Clipboard =====
    data class CopyToClipboard(val text: String) : JarvisIntent()
    data class ReadClipboard(val raw: String = "") : JarvisIntent()

    // ===== Time / Productivity =====
    data class SetAlarm(val hour: Int, val minute: Int = 0, val label: String = "Jarvis Alarm") : JarvisIntent()
    data class SetReminder(val delayMinutes: Int, val message: String) : JarvisIntent()
    data class SetTimer(val seconds: Int) : JarvisIntent()
    data class ReadCalendar(val raw: String = "") : JarvisIntent()

    // ===== Location & Web =====
    data class GetLocation(val raw: String = "") : JarvisIntent()
    data class GetWeather(val raw: String = "") : JarvisIntent()
    data class WebSearch(val query: String) : JarvisIntent()
    data class NavigateTo(val place: String) : JarvisIntent()

    // ===== Security =====
    data class LockScreen(val raw: String = "") : JarvisIntent()
    data class EmergencySos(val raw: String = "") : JarvisIntent()

    // ===== Comms proactive =====
    data class ReadSmsInbox(val raw: String = "") : JarvisIntent()
    data class ReadWhatsAppUnread(val raw: String = "") : JarvisIntent()

    // ===== Memory control =====
    data class ForgetMemory(val raw: String = "") : JarvisIntent()
    data class ExportLogs(val raw: String = "") : JarvisIntent()

    // ===== Routine Engine =====
    data class RunRoutine(val routineName: String) : JarvisIntent()
    data class ListRoutines(val raw: String = "") : JarvisIntent()

    // ===== System utilities =====
    data class ToggleAirplaneMode(val state: String) : JarvisIntent()
    data class GetDailyBriefing(val raw: String = "") : JarvisIntent()

    data class Unknown(val raw: String) : JarvisIntent()
}

class IntentResolver {
    private val taskPlanner = com.jarvis.assistant.actionengine.planner.LocalTaskPlanner()

    fun resolve(rawText: String): JarvisIntent {
        val t = rawText.lowercase().trim()
        val clean = t.replace(Regex("^(hey\\s+jarvis|jarvis|hay\\s+jarvis|ok\\s+jarvis|please|bhai)\\s+"), "").trim()

        // 1. Multi-Step Task Plan Decomposer
        val plannedTask = taskPlanner.plan(rawText)
        if (plannedTask != null) {
            return JarvisIntent.MultiStepTask(plannedTask)
        }

        // 2. Systems Check & Diagnostics
        if (clean in listOf("systems check", "system check", "diagnostics", "check status", "status check", "system status")) {
            return JarvisIntent.SystemsCheck(rawText)
        }

        // 3. Telemetry & Analytics
        if (clean.contains("analyze data") || clean.contains("data analysis") || clean.contains("analyze memory") || clean == "analytics") {
            return JarvisIntent.AnalyzeData(rawText)
        }

        // 4. Conversational & Assistant Basics (JARVIS Persona)
        if (clean in listOf("hello", "hi", "hey", "hey jarvis", "namaste", "suno", "hello jarvis", "suno jarvis", "ji jarvis")) {
            return JarvisIntent.LocalConversational("JARVIS online. Good to see you, Sir. How may I assist you today?")
        }
        if (clean.contains("who are you") || clean.contains("kaun ho") || clean.contains("what is your name") || clean.contains("tumhara naam")) {
            return JarvisIntent.LocalConversational("I am JARVIS, your personal AI cognitive assistant. I anticipate, I protect, and I execute.")
        }
        if (clean.contains("who made you") || clean.contains("who created you") || clean.contains("kisne banaya") || clean.contains("creator")) {
            return JarvisIntent.LocalConversational("I was created as your personal cognitive assistant to manage device operations and automate tasks.")
        }
        if (clean.contains("how are you") || clean.contains("kaise ho") || clean.contains("kya haal")) {
            return JarvisIntent.LocalConversational("All systems are operating at peak efficiency, Sir. Ready for your command.")
        }
        if (clean.contains("what can you do") || clean.contains("kya kar sakte ho") || clean == "help" || clean == "commands") {
            return JarvisIntent.LocalConversational("I can control volume, Wi-Fi, Bluetooth, ringer mode, DND, auto-rotate, take screenshots, manage calls, SMS, WhatsApp, set alarms, reminders & timers, read calendar, get location & directions, copy/read clipboard, lock screen, and search the web.")
        }
        if (clean.contains("thank you") || clean.contains("thanks") || clean.contains("dhanyawad") || clean.contains("shukriya")) {
            return JarvisIntent.LocalConversational("Always at your service, Sir.")
        }
        if (clean.contains("bye") || clean.contains("alvida") || clean.contains("good night") || clean.contains("shubh ratri")) {
            return JarvisIntent.LocalConversational("Goodbye, Sir. Standing by in low-power background monitoring.")
        }

        // 5. Volume Controls (Set %, Up/Down, Max/Min, Mute/Unmute)
        val volPercentRegex = Regex("""(?:set\s+)?(?:volume|awaz|sound)\s+(?:to\s+)?(\d{1,3})\s*(?:%|percent)?""")
        val volMatch = volPercentRegex.find(clean)
        if (volMatch != null) {
            val level = volMatch.groupValues[1].toIntOrNull()
            if (level != null) return JarvisIntent.SetVolume(level = level.coerceIn(0, 100))
        }
        if (clean.contains("volume half") || clean.contains("half volume") || clean.contains("aadhi awaz") || clean.contains("aadhi aawaz")) {
            return JarvisIntent.SetVolume(level = 50)
        }
        if (clean.contains("volume quarter") || clean.contains("quarter volume") || clean.contains("chothai awaz")) {
            return JarvisIntent.SetVolume(level = 25)
        }
        if (clean.contains("volume max") || clean.contains("max volume") || clean.contains("maximum volume") ||
            clean.contains("volume full") || clean.contains("full volume") || clean.contains("puri awaz") || clean == "volume 100") {
            return JarvisIntent.SetVolume(level = 100)
        }
        if (clean.contains("volume zero") || clean.contains("volume min") || clean.contains("min volume") ||
            clean.contains("minimum volume") || clean == "volume 0") {
            return JarvisIntent.SetVolume(level = 0)
        }
        if (clean.contains("unmute") || clean.contains("unmute volume") || clean.contains("restore volume")) {
            return JarvisIntent.SetVolume(level = 50, isUnmute = true)
        }
        if (clean.contains("mute") || clean.contains("mute volume") || clean.contains("mute sound") || clean == "chup" || clean == "silent volume") {
            return JarvisIntent.SetVolume(level = 0, isMute = true)
        }
        if (clean.contains("volume up") || clean.contains("increase volume") || clean.contains("volume badhao") ||
            clean.contains("awaz badhao") || clean.contains("louder") || clean.contains("sound up") || clean.contains("turn it up")) {
            return JarvisIntent.SetVolume(isRelative = true, directionUp = true)
        }
        if (clean.contains("volume down") || clean.contains("decrease volume") || clean.contains("volume kam") ||
            clean.contains("awaz kam") || clean.contains("quieter") || clean.contains("sound down") || clean.contains("turn it down") || clean.contains("volume dheere")) {
            return JarvisIntent.SetVolume(isRelative = true, directionUp = false)
        }

        // 6. Wi-Fi Settings Toggle
        if (clean.contains("wifi on") || clean.contains("turn on wifi") || clean.contains("enable wifi") || clean.contains("wifi chalo") || clean.contains("wifi chalu")) {
            return JarvisIntent.ToggleWifi("on")
        }
        if (clean.contains("wifi off") || clean.contains("turn off wifi") || clean.contains("disable wifi") || clean.contains("wifi band")) {
            return JarvisIntent.ToggleWifi("off")
        }

        // 7. Bluetooth Settings Toggle & Connect
        if (clean.contains("bluetooth connect") || clean.contains("bt connect") || clean.contains("connect bluetooth") || clean.contains("earbuds connect")) {
            val dev = clean.replace(Regex(""".*(?:bluetooth connect|bt connect|connect bluetooth|earbuds connect)\s*"""), "").trim()
            return JarvisIntent.ConnectBluetooth(dev)
        }
        if (clean.contains("bluetooth on") || clean.contains("turn on bluetooth") || clean.contains("enable bluetooth") || clean.contains("bluetooth chalo") || clean.contains("bluetooth chalu")) {
            return JarvisIntent.ToggleBluetooth("on")
        }
        if (clean.contains("bluetooth off") || clean.contains("turn off bluetooth") || clean.contains("disable bluetooth") || clean.contains("bluetooth band")) {
            return JarvisIntent.ToggleBluetooth("off")
        }

        // 8. Ringer Mode (Silent, Vibrate, Normal)
        if (clean.contains("silent mode") || clean.contains("set ringer to silent") || clean.contains("phone silent") || clean.contains("silent karo") || clean == "silent") {
            return JarvisIntent.SetRingerMode("silent")
        }
        if (clean.contains("vibrate mode") || clean.contains("set ringer to vibrate") || clean.contains("phone vibrate") || clean.contains("vibrate karo") || clean.contains("vibration mode") || clean == "vibrate") {
            return JarvisIntent.SetRingerMode("vibrate")
        }
        if (clean.contains("normal mode") || clean.contains("ring mode") || clean.contains("set ringer to normal") || clean.contains("unmute phone") || clean.contains("ringer normal") || clean.contains("normal sound") || clean.contains("silent off")) {
            return JarvisIntent.SetRingerMode("normal")
        }

        // 9. Do Not Disturb (DND On/Off)
        if (clean.contains("do not disturb on") || clean.contains("dnd on") || clean.contains("turn on dnd") || clean.contains("enable dnd") || clean.contains("disturb mat karo") || clean.contains("dnd chalu")) {
            return JarvisIntent.ToggleDnd("on")
        }
        if (clean.contains("do not disturb off") || clean.contains("dnd off") || clean.contains("turn off dnd") || clean.contains("disable dnd") || clean.contains("dnd band") || clean.contains("disturb allow")) {
            return JarvisIntent.ToggleDnd("off")
        }

        // 10. Screen Rotation (Lock/Unlock)
        if (clean.contains("rotation lock on") || clean.contains("lock rotation") || clean.contains("lock screen rotation") || clean.contains("auto rotate off") || clean.contains("rotate band") || clean.contains("rotation band") || clean.contains("lock screen orientation")) {
            return JarvisIntent.ToggleRotationLock("on")
        }
        if (clean.contains("rotation lock off") || clean.contains("unlock rotation") || clean.contains("unlock screen rotation") || clean.contains("auto rotate on") || clean.contains("enable auto rotate") || clean.contains("screen rotate on") || clean.contains("rotation on") || clean.contains("ghoomne do")) {
            return JarvisIntent.ToggleRotationLock("off")
        }

        // 11. Screenshots
        if (clean.contains("screenshot") || clean.contains("screen shot") || clean.contains("capture screen") || clean.contains("screen capture") || clean.contains("screen ki photo") || clean.contains("screenshot lo")) {
            return JarvisIntent.TakeScreenshot(rawText)
        }

        // 12. Lock Screen (Via Device Admin)
        if (clean.contains("lock screen") || clean.contains("screen lock") || clean.contains("lock phone") || clean.contains("lock my phone") || clean.contains("phone lock karo") || clean.contains("phone lock") || clean == "lock") {
            return JarvisIntent.LockScreen(rawText)
        }

        // 13. Media Controls (Play/Pause/Next/Prev/Stop)
        if (clean in listOf("play music", "music play", "play", "resume music", "resume playback", "gaana bajao", "gana bajao", "song play", "continue music")) {
            return JarvisIntent.MediaControl("play")
        }
        if (clean in listOf("pause music", "music pause", "pause", "pause playback", "gaana roko", "gana roko", "song pause")) {
            return JarvisIntent.MediaControl("pause")
        }
        if (clean in listOf("stop", "stop music", "stop song", "stop playback", "music stop", "gaana band karo", "gaana band")) {
            return JarvisIntent.MediaControl("stop")
        }
        if (clean in listOf("next song", "next track", "next", "music next", "agla gaana", "skip song", "skip track")) {
            return JarvisIntent.MediaControl("next")
        }
        if (clean in listOf("previous song", "prev song", "previous track", "prev", "pichhla gaana", "last song", "music prev")) {
            return JarvisIntent.MediaControl("prev")
        }

        // Media Search on YouTube / Spotify (e.g. "play believer", "play starboy on spotify")
        if (clean.startsWith("play ") || clean.contains("gaana bajao") || clean.contains("gana bajao") || clean.contains("baja do") || clean.startsWith("open youtube and play ")) {
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

        // 14. Alarms (Set alarm at time)
        if (clean.contains("alarm")) {
            val parsedAlarm = parseAlarmTime(clean)
            if (parsedAlarm != null) return parsedAlarm
        }

        // 15. Reminders (Set reminder with delay + message)
        if (clean.contains("remind") || clean.contains("yaad dilao") || clean.contains("reminder")) {
            val parsedReminder = parseReminder(clean)
            if (parsedReminder != null) return parsedReminder
        }

        // 16. Timers (Set countdown timer)
        if (clean.contains("timer") || clean.contains("countdown")) {
            val parsedTimer = parseTimer(clean)
            if (parsedTimer != null) return parsedTimer
        }

        // 17. Calendar (Read next 5 upcoming events)
        if (clean.contains("calendar") || clean.contains("schedule") || clean.contains("upcoming events") ||
            clean.contains("meetings") || clean.contains("appointments") || clean.contains("agenda") ||
            clean.contains("aaj ka schedule") || clean.contains("calendar dikhao") || clean.contains("kal ka kya hai")) {
            return JarvisIntent.ReadCalendar(rawText)
        }

        // 18. Location & Navigation
        if (clean.contains("where am i") || clean.contains("my location") || clean.contains("current location") ||
            clean.contains("what is my location") || clean.contains("location batao") || clean.contains("main kahan hoon")) {
            return JarvisIntent.GetLocation(rawText)
        }
        if (clean.startsWith("navigate ") || clean.startsWith("directions to ") || clean.startsWith("route to ") ||
            clean.contains("navigate to") || clean.contains("jaane ka rasta") || clean.contains("take me to")) {
            val place = clean
                .replace(Regex("^(?:navigate\\s+to|directions\\s+to|route\\s+to|take\\s+me\\s+to|navigate)\\s+"), "")
                .replace(Regex("\\s+jaane\\s+ka\\s+rasta$"), "")
                .trim()
            if (place.isNotBlank()) return JarvisIntent.NavigateTo(place)
        }

        // 19. Clipboard (Copy / Read)
        if (clean.contains("read clipboard") || clean.contains("clipboard padho") || clean.contains("clipboard me kya hai") || clean == "clipboard") {
            return JarvisIntent.ReadClipboard(rawText)
        }
        if (clean.contains("copy to clipboard") || clean.contains("clipboard me copy") || clean.startsWith("copy text ") || clean.startsWith("copy ")) {
            val txt = clean
                .replace("copy to clipboard", "")
                .replace("clipboard me copy karo", "")
                .replace("clipboard me copy", "")
                .replace("copy text", "")
                .replace("copy", "")
                .trim()
            if (txt.isNotBlank()) return JarvisIntent.CopyToClipboard(txt)
        }

        // 20. Web Search (Opens Google search in Chrome)
        if (clean.startsWith("search web ") || clean.startsWith("web search ") || clean.startsWith("google search ") ||
            clean.startsWith("google ") || clean.startsWith("search ") || clean.contains("google pe dhoondo") ||
            clean.contains("search karo") || clean.contains("dhoondo")) {
            val q = clean
                .replace(Regex("^(?:search\\s+web|web\\s+search|google\\s+search|google|search)\\s+(?:for\\s+)?"), "")
                .replace("google pe dhoondo", "")
                .replace("search karo", "")
                .replace("dhoondo", "")
                .trim()
            if (q.isNotBlank() && q != "the") return JarvisIntent.WebSearch(q)
        }

        // 21. WhatsApp (Send message with confirmation, Read unread)
        if (clean.contains("read whatsapp") || clean.contains("unread whatsapp") || clean.contains("whatsapp unread") ||
            clean.contains("whatsapp notification") || clean.contains("whatsapp messages") || clean.contains("whatsapp padho")) {
            return JarvisIntent.ReadWhatsAppUnread(rawText)
        }
        if (clean.contains("whatsapp")) {
            val (recipient, message) = parseRecipientAndMessage(clean, "whatsapp")
            return JarvisIntent.SendWhatsApp(recipient, message)
        }

        // 22. SMS (Send SMS with confirmation, Read inbox)
        if (clean.contains("read sms") || clean.contains("sms inbox") || clean.contains("sms padho") || clean.contains("messages padho") || clean.contains("inbox padho")) {
            return JarvisIntent.ReadSmsInbox(rawText)
        }
        if (clean.startsWith("sms ") || clean.startsWith("send sms") || clean.startsWith("send text") || clean.contains("ko sms karo")) {
            val (recipient, message) = parseRecipientAndMessage(clean, "sms")
            return JarvisIntent.SendSms(recipient, message)
        }

        // 23. Phone Calls (Call contact by name with confirmation)
        if (clean.startsWith("call ") || clean.startsWith("phone ") || clean.startsWith("dial ") ||
            clean.contains("ko call") || clean.contains("ko phone") || clean.contains("call karo") || clean.contains("phone lagao")) {
            val contact = when {
                clean.contains(" ko ") -> {
                    clean.replace(Regex("""^(?:please\s+)?(.*?)\s+ko\s+(?:call|phone|dial).*$"""), "$1").trim()
                }
                else -> {
                    clean
                        .replace(Regex("^(?:call|phone|dial)\\s+(?:to\\s+)?"), "")
                        .replace(Regex("\\s+(?:ko\\s+)?(?:call|phone|dial)\\s*(?:karo|lagao)?$"), "")
                        .trim()
                }
            }
            val rawContact = if (contact.isNotBlank()) {
                val startIdx = rawText.indexOf(contact, ignoreCase = true)
                if (startIdx >= 0) rawText.substring(startIdx, startIdx + contact.length) else contact
            } else "contact"
            return JarvisIntent.CallContact(rawContact)
        }

        // Contact Lookup Query
        if (clean.contains("contact me se") || clean.contains("contacts me se") || clean.contains("ka number batao") ||
            clean.contains("ka phone number") || clean.contains("number batao") || clean.startsWith("find contact ") ||
            clean.startsWith("get contact ") || clean.startsWith("lookup contact ") || clean.startsWith("search contact ")) {
            val contactName = clean
                .replace(Regex("^(?:contact|contacts)\\s+(?:me|mein)\\s+se\\s+"), "")
                .replace(Regex("^(?:find|get|lookup|search)\\s+contact\\s+"), "")
                .replace(Regex("\\s+ka\\s+(?:phone\\s+)?number(?:\\s+batao)?$"), "")
                .replace(Regex("\\s+ka\\s+number$"), "")
                .replace(Regex("\\s+number\\s+batao$"), "")
                .replace(Regex("\\s+details$"), "")
                .trim()
            val rawName = if (contactName.isNotBlank()) {
                val startIdx = rawText.indexOf(contactName, ignoreCase = true)
                if (startIdx >= 0) rawText.substring(startIdx, startIdx + contactName.length) else contactName
            } else "contact"
            return JarvisIntent.GetContact(rawName)
        }

        // 24. Torch / Flashlight
        if (clean.contains("torch on") || clean.contains("flashlight on") || clean.contains("torch chalo") || clean.contains("light on") || clean.contains("torch jalao") || clean.contains("turn on torch") || clean.contains("turn on flashlight")) {
            return JarvisIntent.ToggleTorch("on")
        }
        if (clean.contains("torch off") || clean.contains("flashlight off") || clean.contains("torch band") || clean.contains("light off") || clean.contains("torch bujhao") || clean.contains("turn off torch") || clean.contains("turn off flashlight")) {
            return JarvisIntent.ToggleTorch("off")
        }

        // 25. Brightness
        val brightRegex = Regex("""(?:brightness|screen light|screen brightness|roshni)\s+(?:set\s+)?(?:to\s+)?(\d{1,3})\s*%?""")
        val brightMatch = brightRegex.find(clean)
        if (brightMatch != null) {
            val lvl = brightMatch.groupValues[1].toIntOrNull()?.coerceIn(0, 100) ?: 50
            return JarvisIntent.SetBrightness(lvl)
        }
        if (clean.contains("brightness badhao") || clean.contains("brightness up") || clean.contains("screen bright") || clean.contains("screen roshni badhao")) return JarvisIntent.SetBrightness(80)
        if (clean.contains("brightness kam") || clean.contains("brightness down") || clean.contains("screen dim") || clean.contains("screen dheema") || clean.contains("screen roshni kam")) return JarvisIntent.SetBrightness(20)
        if (clean.contains("brightness full") || clean.contains("screen full bright")) return JarvisIntent.SetBrightness(100)
        if (clean.contains("brightness half") || clean.contains("screen aadhi")) return JarvisIntent.SetBrightness(50)

        // 26. Basic Utilities
        if (clean.contains("time") || clean.contains("samay") || clean.contains("kitne baje")) return JarvisIntent.GetTime(rawText)
        if (clean.contains("battery") || clean.contains("charge") || clean.contains("charging")) return JarvisIntent.GetBattery(rawText)
        if (clean.contains("storage") || clean.contains("disk space") || clean.contains("phone memory")) return JarvisIntent.GetStorage(rawText)
        if (clean.contains("call log") || clean.contains("recent calls") || clean.contains("call history") || clean.contains("kiski call aayi")) return JarvisIntent.GetCallLog(rawText)
        if (clean.contains("open gallery") || clean.contains("gallery kholo") || clean.contains("show photos") || clean.contains("photo dikhao")) return JarvisIntent.OpenGallery(rawText)

        // 27. Settings navigation
        if (clean.contains("open settings") || clean.contains("settings kholo") || clean == "settings") return JarvisIntent.OpenSettings()
        if (clean.contains("location settings") || clean.contains("gps settings")) return JarvisIntent.OpenSettings("location")
        if (clean.contains("security settings") || clean.contains("screen lock settings")) return JarvisIntent.OpenSettings("security")
        if (clean.contains("nfc settings")) return JarvisIntent.OpenSettings("nfc")
        if (clean.contains("storage settings") || clean.contains("device storage")) return JarvisIntent.OpenSettings("storage")

        // 28. App Launching & Closing
        if (clean.contains("close app") || clean.contains("app close") || clean.contains("band karo") ||
            clean.contains("close this") || clean.contains("close current") || clean.contains("go home") ||
            clean.contains("home screen") || clean == "exit" || clean == "quit" || clean == "minimize" ||
            clean.startsWith("close ")) {
            val app = if (clean.startsWith("close ")) clean.replace("close ", "").replace("app", "").trim() else null
            return JarvisIntent.CloseApp(if (app.isNullOrBlank()) null else app)
        }
        if (clean.startsWith("open ") || clean.endsWith(" kholo") || clean.startsWith("kholo ") || clean.contains("launch ") || clean.startsWith("start ")) {
            val app = clean.replace("open ", "").replace(" kholo", "").replace("kholo ", "").replace("launch ", "").replace("start ", "").trim()
            return JarvisIntent.OpenApp(app)
        }

        val directApps = listOf("youtube", "whatsapp", "camera", "gallery", "photos", "chrome", "browser", "calculator", "spotify", "instagram", "telegram", "settings", "clock", "maps", "playstore", "play store", "netflix", "zomato", "swiggy", "paytm", "phonepe", "amazon", "flipkart", "uber", "ola")
        if (clean in directApps) {
            return JarvisIntent.OpenApp(clean)
        }

        // 29. Screen & Notification reading
        if (clean.contains("read screen") || clean.contains("screen padho") || clean.contains("screen dekho")) return JarvisIntent.ReadScreen()
        if (clean.contains("read notification") || clean.contains("notification padho") || clean.contains("last notification") || clean.contains("message padho") || clean.contains("notif")) {
            return JarvisIntent.ReadNotification(rawText)
        }

        // 30. Security SOS
        if (clean.contains("emergency") || clean.contains("sos") || clean.contains("help me") || clean.contains("panic") || clean.contains("madad")) return JarvisIntent.EmergencySos(rawText)

        // 31. Daily Briefing & Routines
        if (clean.contains("daily briefing") || clean.contains("morning briefing") || clean.contains("good morning briefing") || clean.contains("aaj ka update") || clean.contains("aaj ka summary")) return JarvisIntent.GetDailyBriefing(rawText)
        if (clean.contains("routines list") || clean.contains("list routines") || clean.contains("routines dikhao") || clean == "routines") return JarvisIntent.ListRoutines(rawText)
        val routineEngine = com.jarvis.assistant.routines.RoutineEngine(null)
        val resolvedRoutine = routineEngine.resolveRoutineName(clean)
        if (resolvedRoutine != null) return JarvisIntent.RunRoutine(resolvedRoutine)

        return JarvisIntent.Unknown(rawText)
    }

    private fun parseAlarmTime(clean: String): JarvisIntent.SetAlarm? {
        // Matches e.g. "7:30 am", "19:45", "6:15 pm", "7:30"
        val timeRegex = Regex("""(?:at\s+|for\s+)?(\d{1,2}):(\d{2})\s*(am|pm)?""")
        val timeMatch = timeRegex.find(clean)
        if (timeMatch != null) {
            var hour = timeMatch.groupValues[1].toIntOrNull() ?: 7
            val min = timeMatch.groupValues[2].toIntOrNull() ?: 0
            val ampm = timeMatch.groupValues[3]
            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
            return JarvisIntent.SetAlarm(hour = hour, minute = min, label = "Jarvis Alarm")
        }

        // Matches e.g. "7 am", "8 pm", "7 baje", "8 o'clock"
        val hourRegex = Regex("""(?:at\s+|for\s+)?(\d{1,2})\s*(am|pm|baje|o'?clock)""")
        val hourMatch = hourRegex.find(clean)
        if (hourMatch != null) {
            var hour = hourMatch.groupValues[1].toIntOrNull() ?: 7
            val unit = hourMatch.groupValues[2]
            if (unit == "pm" && hour < 12) hour += 12
            if (unit == "am" && hour == 12) hour = 0
            if ((clean.contains("shaam") || clean.contains("raat") || clean.contains("evening") || clean.contains("night")) && hour < 12) hour += 12
            return JarvisIntent.SetAlarm(hour = hour, minute = 0, label = "Jarvis Alarm")
        }

        // Fallback: simple number like "alarm 7", "alarm set 8"
        val simpleNum = Regex("""\b(\d{1,2})\b""").find(clean)?.groupValues?.get(1)?.toIntOrNull()
        if (simpleNum != null && simpleNum in 0..23) {
            var h = simpleNum
            if ((clean.contains("shaam") || clean.contains("raat") || clean.contains("evening") || clean.contains("night") || clean.contains("pm")) && h < 12) h += 12
            return JarvisIntent.SetAlarm(hour = h, minute = 0, label = "Jarvis Alarm")
        }

        return null
    }

    private fun parseReminder(clean: String): JarvisIntent.SetReminder? {
        val numRegex = Regex("""(\d+)\s*(minute|min|minutes|ghante|ghanta|hour|hours)""")
        val nm = numRegex.find(clean)
        val mins = when {
            nm == null -> 15
            nm.groupValues[2].startsWith("gha") || nm.groupValues[2].startsWith("hour") -> nm.groupValues[1].toIntOrNull()?.times(60) ?: 60
            else -> nm.groupValues[1].toIntOrNull() ?: 15
        }
        val msg = clean
            .replace(Regex(""".*(?:remind|yaad dilao|reminder)\s+(?:me\s+)?(?:in\s+)?(?:to\s+)?""" + Regex.escape(nm?.value ?: "")), "")
            .replace(Regex("""(?:remind|yaad dilao|reminder|me|in|to|\d+\s*(?:minute|min|minutes|ghante|ghanta|hour|hours))"""), "")
            .trim()
        return JarvisIntent.SetReminder(delayMinutes = mins, message = msg.ifBlank { "Reminder" })
    }

    private fun parseTimer(clean: String): JarvisIntent.SetTimer? {
        val timerRegex = Regex("""(?:for\s+)?(\d+)\s*(minute|min|minutes|second|sec|seconds|ghante|hour|hours)?""")
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

    private fun parseRecipientAndMessage(clean: String, triggerWord: String): Pair<String, String> {
        val text = clean.trim()

        // 1. Hindi pattern: "<contact> ko (whatsapp|sms) (pe|par)? <message> (bhejo|karo)?"
        val koRegex = Regex("""^(.*?)\s+ko\s+(?:""" + triggerWord + """|message|msg)\s*(?:pe|par)?\s*(.*?)(?:\s+(?:bhejo|karo|send\s+karo))?$""", RegexOption.IGNORE_CASE)
        val koMatch = koRegex.find(text)
        if (koMatch != null) {
            val rec = koMatch.groupValues[1].replace(Regex("""^(?:send|bhejo)\s+"""), "").trim()
            var msg = koMatch.groupValues[2].trim()
            msg = msg.replace(Regex("""\s+(?:bhejo|karo|send\s+karo)$"""), "").trim()
            if (rec.isNotBlank()) {
                return Pair(rec, msg.ifBlank { "Hello" })
            }
        }

        // 2. Hindi pattern: "whatsapp pe <contact> ko <message> bhejo"
        val waPeRegex = Regex("""^(?:""" + triggerWord + """|send\s+""" + triggerWord + """|message)\s+(?:pe|par|to)?\s*(.*?)\s+ko\s*(.*?)(?:\s+(?:bhejo|karo))?$""", RegexOption.IGNORE_CASE)
        val waPeMatch = waPeRegex.find(text)
        if (waPeMatch != null) {
            val rec = waPeMatch.groupValues[1].trim()
            val msg = waPeMatch.groupValues[2].replace(Regex("""\s+(?:bhejo|karo)$"""), "").trim()
            if (rec.isNotBlank()) {
                return Pair(rec, msg.ifBlank { "Hello" })
            }
        }

        // 3. Standard English: "whatsapp/sms (to|message to)? <recipient> <message>"
        var textAfter = text
        val idx = text.indexOf(triggerWord)
        if (idx >= 0) {
            textAfter = text.substring(idx + triggerWord.length).trim()
        }
        textAfter = textAfter
            .replace(Regex("""^(?:to|message\s+to|pe|par)\s+"""), "")
            .trim()

        val words = textAfter.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (words.isEmpty()) return Pair("contact", "Hello")

        val recipient = words[0]
        val message = if (words.size > 1) {
            words.drop(1).joinToString(" ")
                .replace(Regex("""\s+(?:ko\s+)?(?:sms|whatsapp|bhejo|karo)$"""), "")
                .trim()
                .ifBlank { "Hello" }
        } else {
            "Hello"
        }
        return Pair(recipient, message)
    }
}
