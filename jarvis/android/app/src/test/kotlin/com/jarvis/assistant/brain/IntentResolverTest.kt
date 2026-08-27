package com.jarvis.assistant.brain

import org.junit.Assert.*
import org.junit.Test

class IntentResolverTest {
    private val resolver = IntentResolver()

    @Test
    fun testTorchCommandResolution() {
        val intent = resolver.resolve("Jarvis torch on")
        assertTrue(intent is JarvisIntent.ToggleTorch)
        assertEquals("on", (intent as JarvisIntent.ToggleTorch).state)
    }

    @Test
    fun testTimeCommandResolution() {
        val intent = resolver.resolve("Jarvis time kya hai")
        assertTrue(intent is JarvisIntent.GetTime)
    }

    @Test
    fun testRiskyCallCommandResolution() {
        val intent = resolver.resolve("call Alice")
        assertTrue(intent is JarvisIntent.CallContact)
        assertEquals("Alice", (intent as JarvisIntent.CallContact).contactName)
    }

    @Test
    fun testWhatsAppCommandResolution() {
        val intent = resolver.resolve("send whatsapp to Bob Hello there")
        assertTrue(intent is JarvisIntent.SendWhatsApp)
        val wa = intent as JarvisIntent.SendWhatsApp
        assertEquals("Bob", wa.contactName)
        assertEquals("Hello there", wa.message)
    }

    @Test
    fun testHinglishTorchCommandResolution() {
        val intent = resolver.resolve("torch chalo")
        assertTrue(intent is JarvisIntent.ToggleTorch)
        assertEquals("on", (intent as JarvisIntent.ToggleTorch).state)
    }

    @Test
    fun testMediaControlResolution() {
        val play = resolver.resolve("Jarvis gaana bajao")
        assertTrue(play is JarvisIntent.MediaControl)
        assertEquals("play", (play as JarvisIntent.MediaControl).action)

        val pause = resolver.resolve("music pause")
        assertTrue(pause is JarvisIntent.MediaControl)
        assertEquals("pause", (pause as JarvisIntent.MediaControl).action)

        val next = resolver.resolve("agla gaana")
        assertTrue(next is JarvisIntent.MediaControl)
        assertEquals("next", (next as JarvisIntent.MediaControl).action)

        val prev = resolver.resolve("previous song")
        assertTrue(prev is JarvisIntent.MediaControl)
        assertEquals("prev", (prev as JarvisIntent.MediaControl).action)
    }

    @Test
    fun testBluetoothAndVolumeResolution() {
        val bt = resolver.resolve("bluetooth chalo")
        assertTrue(bt is JarvisIntent.ToggleBluetooth)
        assertEquals("on", (bt as JarvisIntent.ToggleBluetooth).state)

        val mute = resolver.resolve("chup")
        assertTrue(mute is JarvisIntent.SetVolume)
        assertEquals(0, (mute as JarvisIntent.SetVolume).level)
    }

    @Test
    fun testOpenAppResolution() {
        val app = resolver.resolve("youtube kholo")
        assertTrue(app is JarvisIntent.OpenApp)
        assertEquals("youtube", (app as JarvisIntent.OpenApp).appName)
    }

    @Test
    fun testAppDiscoveryResolution() {
        val allApps = resolver.resolve("apps list")
        assertTrue(allApps is JarvisIntent.ListApps)
        assertNull((allApps as JarvisIntent.ListApps).category)

        val musicApps = resolver.resolve("music apps")
        assertTrue(musicApps is JarvisIntent.ListApps)
        assertEquals("music", (musicApps as JarvisIntent.ListApps).category)

        val gameApps = resolver.resolve("game apps")
        assertTrue(gameApps is JarvisIntent.ListApps)
        assertEquals("games", (gameApps as JarvisIntent.ListApps).category)
    }

    @Test
    fun testGalleryStorageAndCallLogResolution() {
        val gallery = resolver.resolve("photo dikhao")
        assertTrue(gallery is JarvisIntent.OpenGallery)

        val storage = resolver.resolve("check phone storage")
        assertTrue(storage is JarvisIntent.GetStorage)

        val callLog = resolver.resolve("recent calls")
        assertTrue(callLog is JarvisIntent.GetCallLog)

        val settings = resolver.resolve("open settings")
        assertTrue(settings is JarvisIntent.OpenSettings)
    }

    @Test
    fun testNotificationAndScreenResolution() {
        val notif = resolver.resolve("notification padho")
        assertTrue(notif is JarvisIntent.ReadNotification)

        val screen = resolver.resolve("screen padho")
        assertTrue(screen is JarvisIntent.ReadScreen)
    }

    @Test
    fun testPersonaAndCloseAppResolution() {
        val who = resolver.resolve("who are you")
        assertTrue(who is JarvisIntent.LocalConversational)
        assertTrue((who as JarvisIntent.LocalConversational).answer.contains("Minaty"))

        val close = resolver.resolve("close youtube app")
        assertTrue(close is JarvisIntent.CloseApp)

        val home = resolver.resolve("go home")
        assertTrue(home is JarvisIntent.CloseApp)
    }

    // ====== New Phase 1+2 Intent Tests ======

    @Test
    fun testBrightnessResolution() {
        val up = resolver.resolve("brightness badhao")
        assertTrue(up is JarvisIntent.SetBrightness)
        assertEquals(80, (up as JarvisIntent.SetBrightness).level)

        val down = resolver.resolve("screen dim")
        assertTrue(down is JarvisIntent.SetBrightness)
        assertEquals(20, (down as JarvisIntent.SetBrightness).level)

        val full = resolver.resolve("brightness full")
        assertTrue(full is JarvisIntent.SetBrightness)
        assertEquals(100, (full as JarvisIntent.SetBrightness).level)

        val pct = resolver.resolve("brightness 75%")
        assertTrue(pct is JarvisIntent.SetBrightness)
        assertEquals(75, (pct as JarvisIntent.SetBrightness).level)
    }

    @Test
    fun testDndAndRingerResolution() {
        val dndOn = resolver.resolve("dnd on")
        assertTrue(dndOn is JarvisIntent.ToggleDnd)
        assertEquals("on", (dndOn as JarvisIntent.ToggleDnd).state)

        val dndOff = resolver.resolve("dnd off")
        assertTrue(dndOff is JarvisIntent.ToggleDnd)
        assertEquals("off", (dndOff as JarvisIntent.ToggleDnd).state)

        val silent = resolver.resolve("silent mode on")
        assertTrue(silent is JarvisIntent.SetRingerMode)
        assertEquals("silent", (silent as JarvisIntent.SetRingerMode).mode)

        val vibrate = resolver.resolve("vibrate mode on")
        assertTrue(vibrate is JarvisIntent.SetRingerMode)
        assertEquals("vibrate", (vibrate as JarvisIntent.SetRingerMode).mode)
    }

    @Test
    fun testRotationLockResolution() {
        val lockOn = resolver.resolve("rotation lock on")
        assertTrue(lockOn is JarvisIntent.ToggleRotationLock)
        assertEquals("on", (lockOn as JarvisIntent.ToggleRotationLock).state)

        val lockOff = resolver.resolve("auto rotate on")
        assertTrue(lockOff is JarvisIntent.ToggleRotationLock)
        assertEquals("off", (lockOff as JarvisIntent.ToggleRotationLock).state)
    }

    @Test
    fun testBatteryChargingStatusResolution() {
        val charging = resolver.resolve("charging hai ya nahi")
        assertTrue(charging is JarvisIntent.BatteryChargingStatus)

        val status = resolver.resolve("battery status")
        assertTrue(status is JarvisIntent.BatteryChargingStatus)
    }

    @Test
    fun testScreenshotResolution() {
        val ss = resolver.resolve("screenshot lo")
        assertTrue(ss is JarvisIntent.TakeScreenshot)

        val ssEng = resolver.resolve("take a screenshot")
        assertTrue(ssEng is JarvisIntent.TakeScreenshot)
    }

    @Test
    fun testAlarmAndTimerResolution() {
        val alarm = resolver.resolve("alarm lagao 7 baje")
        assertTrue(alarm is JarvisIntent.SetAlarm)

        val timer = resolver.resolve("timer for 10 minutes")
        assertTrue(timer is JarvisIntent.SetTimer)
        assertEquals(600, (timer as JarvisIntent.SetTimer).seconds)

        val timerSec = resolver.resolve("timer 30 seconds")
        assertTrue(timerSec is JarvisIntent.SetTimer)
        assertEquals(30, (timerSec as JarvisIntent.SetTimer).seconds)
    }

    @Test
    fun testReminderResolution() {
        val reminder = resolver.resolve("remind me in 30 minutes")
        assertTrue(reminder is JarvisIntent.SetReminder)
        assertEquals(30, (reminder as JarvisIntent.SetReminder).delayMinutes)

        val reminderHindi = resolver.resolve("yaad dilao 2 ghante baad")
        assertTrue(reminderHindi is JarvisIntent.SetReminder)
        assertEquals(120, (reminderHindi as JarvisIntent.SetReminder).delayMinutes)
    }

    @Test
    fun testLocationAndWeatherResolution() {
        val loc = resolver.resolve("main kahan hoon")
        assertTrue(loc is JarvisIntent.GetLocation)

        val weather = resolver.resolve("aaj ka mausam")
        assertTrue(weather is JarvisIntent.GetWeather)
    }

    @Test
    fun testWebSearchResolution() {
        val search = resolver.resolve("google pe cricket dhoondo")
        assertTrue(search is JarvisIntent.WebSearch)
        assertTrue((search as JarvisIntent.WebSearch).query.contains("cricket"))
    }

    @Test
    fun testClipboardResolution() {
        val read = resolver.resolve("clipboard padho")
        assertTrue(read is JarvisIntent.ReadClipboard)
    }

    @Test
    fun testRoutineEngineResolution() {
        val movie = resolver.resolve("movie mode on karo")
        assertTrue("Expected RunRoutine for 'movie mode on karo', got: ${movie::class.simpleName}", movie is JarvisIntent.RunRoutine)
        assertEquals("movie", (movie as JarvisIntent.RunRoutine).routineName)

        val morning = resolver.resolve("morning routine chalao")
        assertTrue(morning is JarvisIntent.RunRoutine)
        assertEquals("morning", (morning as JarvisIntent.RunRoutine).routineName)

        val night = resolver.resolve("night mode on")
        assertTrue(night is JarvisIntent.RunRoutine)
        assertEquals("night", (night as JarvisIntent.RunRoutine).routineName)

        val meeting = resolver.resolve("meeting mode")
        assertTrue(meeting is JarvisIntent.RunRoutine)
        assertEquals("meeting", (meeting as JarvisIntent.RunRoutine).routineName)

        val list = resolver.resolve("routines list dikhao")
        assertTrue(list is JarvisIntent.ListRoutines)
    }

    @Test
    fun testDailyBriefingResolution() {
        val briefing = resolver.resolve("daily briefing sunao")
        assertTrue(briefing is JarvisIntent.GetDailyBriefing)

        val morning = resolver.resolve("morning briefing")
        assertTrue(morning is JarvisIntent.GetDailyBriefing)
    }

    @Test
    fun testAirplaneModeResolution() {
        val on = resolver.resolve("airplane mode on")
        assertTrue(on is JarvisIntent.ToggleAirplaneMode)
        assertEquals("on", (on as JarvisIntent.ToggleAirplaneMode).state)

        val off = resolver.resolve("flight mode off")
        assertTrue(off is JarvisIntent.ToggleAirplaneMode)
        assertEquals("off", (off as JarvisIntent.ToggleAirplaneMode).state)
    }
}
