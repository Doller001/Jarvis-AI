package com.jarvis.assistant

import com.jarvis.assistant.brain.IntentResolver
import com.jarvis.assistant.brain.JarvisIntent
import com.jarvis.assistant.brain.JarvisBrain
import com.jarvis.assistant.brain.Planner
import com.jarvis.assistant.brain.ResponseGenerator
import com.jarvis.assistant.execution.CommandExecutor
import com.jarvis.assistant.execution.ConfirmationManager
import com.jarvis.assistant.execution.ExecutionOutcome
import com.jarvis.assistant.execution.TaskExecutionCoordinator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AllActionsAndTtsTest {

    private lateinit var resolver: IntentResolver
    private lateinit var brain: JarvisBrain
    private lateinit var coordinator: TaskExecutionCoordinator

    @Before
    fun setUp() {
        resolver = IntentResolver()
        brain = JarvisBrain(
            planner = Planner(),
            responseGenerator = ResponseGenerator()
        )
        coordinator = TaskExecutionCoordinator(
            context = null,
            brain = brain,
            commandExecutor = CommandExecutor(null),
            confirmationManager = ConfirmationManager()
        )
    }

    @Test
    fun testAllActionsAndOutputs() = runBlocking {
        println("================================================================================")
        println("                   JARVIS ASSISTANT - ACTION & TTS TEST SUITE                   ")
        println("================================================================================")

        val testCases = listOf(
            // 1. Volume
            "volume 70%" to JarvisIntent.SetVolume::class,
            "volume up" to JarvisIntent.SetVolume::class,
            "volume down" to JarvisIntent.SetVolume::class,
            "max volume" to JarvisIntent.SetVolume::class,
            "mute" to JarvisIntent.SetVolume::class,
            "unmute" to JarvisIntent.SetVolume::class,

            // 2. Wi-Fi
            "wifi on" to JarvisIntent.ToggleWifi::class,
            "wifi off" to JarvisIntent.ToggleWifi::class,
            "turn on wifi" to JarvisIntent.ToggleWifi::class,

            // 3. Bluetooth
            "bluetooth on" to JarvisIntent.ToggleBluetooth::class,
            "bluetooth off" to JarvisIntent.ToggleBluetooth::class,
            "bluetooth connect AirPods" to JarvisIntent.ConnectBluetooth::class,

            // 4. Ringer Mode
            "silent mode" to JarvisIntent.SetRingerMode::class,
            "vibrate mode" to JarvisIntent.SetRingerMode::class,
            "normal mode" to JarvisIntent.SetRingerMode::class,

            // 5. DND
            "dnd on" to JarvisIntent.ToggleDnd::class,
            "dnd off" to JarvisIntent.ToggleDnd::class,

            // 6. Screen Rotation
            "lock rotation" to JarvisIntent.ToggleRotationLock::class,
            "unlock rotation" to JarvisIntent.ToggleRotationLock::class,

            // 7. Media
            "play music" to JarvisIntent.MediaControl::class,
            "pause music" to JarvisIntent.MediaControl::class,
            "stop music" to JarvisIntent.MediaControl::class,
            "next track" to JarvisIntent.MediaControl::class,
            "previous track" to JarvisIntent.MediaControl::class,

            // 8. Alarms
            "set alarm at 7:30 am" to JarvisIntent.SetAlarm::class,
            "alarm for 6:45 pm" to JarvisIntent.SetAlarm::class,
            "alarm 8 baje" to JarvisIntent.SetAlarm::class,

            // 9. Reminders
            "remind me in 15 minutes to call doctor" to JarvisIntent.SetReminder::class,
            "remind me in 2 hours to submit report" to JarvisIntent.SetReminder::class,

            // 10. Timers
            "set timer for 5 minutes" to JarvisIntent.SetTimer::class,
            "timer 30 seconds" to JarvisIntent.SetTimer::class,

            // 11. Calendar
            "read calendar" to JarvisIntent.ReadCalendar::class,
            "my schedule" to JarvisIntent.ReadCalendar::class,
            "upcoming events" to JarvisIntent.ReadCalendar::class,

            // 12. Location & Navigation
            "where am i" to JarvisIntent.GetLocation::class,
            "my location" to JarvisIntent.GetLocation::class,
            "navigate to Airport" to JarvisIntent.NavigateTo::class,
            "directions to Central Mall" to JarvisIntent.NavigateTo::class,

            // 13. Screenshots
            "take screenshot" to JarvisIntent.TakeScreenshot::class,
            "capture screen" to JarvisIntent.TakeScreenshot::class,

            // 14. Clipboard
            "copy to clipboard Secret Note" to JarvisIntent.CopyToClipboard::class,
            "read clipboard" to JarvisIntent.ReadClipboard::class,

            // 15. Lock Screen
            "lock screen" to JarvisIntent.LockScreen::class,
            "lock phone" to JarvisIntent.LockScreen::class,

            // 16. Web Search
            "search web quantum computing" to JarvisIntent.WebSearch::class,
            "google search best restaurants" to JarvisIntent.WebSearch::class,

            // 17. WhatsApp Unread
            "read whatsapp" to JarvisIntent.ReadWhatsAppUnread::class,
            "unread whatsapp" to JarvisIntent.ReadWhatsAppUnread::class
        )

        for ((input, expectedIntentClass) in testCases) {
            val intent = resolver.resolve(input)
            assertEquals("Intent mismatch for: '$input'", expectedIntentClass, intent::class)

            val outcome = coordinator.coordinate(input)
            val ttsOutput = when (outcome) {
                is ExecutionOutcome.Success -> outcome.spokenResponse
                is ExecutionOutcome.ConfirmationRequired -> outcome.prompt
                is ExecutionOutcome.Failure -> outcome.spokenResponse
                is ExecutionOutcome.RouteToCloud -> "Routed to cloud brain"
            }

            println(String.format("%-38s -> [%-20s] -> TTS: \"%s\"", input, intent::class.simpleName, ttsOutput))
            assertNotNull(ttsOutput)
            assertTrue("TTS output should not be blank for '$input'", ttsOutput.isNotBlank())
        }

        // 18. Confirmation Flow: Phone Calls
        println("\n--- Testing Confirmation Flow: Phone Call ---")
        val callOutcome = coordinator.coordinate("call Mom")
        assertTrue(callOutcome is ExecutionOutcome.ConfirmationRequired)
        val promptCall = (callOutcome as ExecutionOutcome.ConfirmationRequired).prompt
        println("Prompt: \"$promptCall\"")
        assertEquals("Confirm calling Mom? (Say 'yes' or 'confirm' to proceed)", promptCall)

        val confirmCall = coordinator.coordinate("yes")
        assertTrue(confirmCall is ExecutionOutcome.Success)
        val callTts = (confirmCall as ExecutionOutcome.Success).spokenResponse
        println("Confirmed Response TTS: \"$callTts\"")
        assertTrue(callTts.contains("Mom"))

        // 19. Confirmation Flow: SMS
        println("\n--- Testing Confirmation Flow: SMS ---")
        val smsOutcome = coordinator.coordinate("sms John I will be late")
        assertTrue(smsOutcome is ExecutionOutcome.ConfirmationRequired)
        val promptSms = (smsOutcome as ExecutionOutcome.ConfirmationRequired).prompt
        println("Prompt: \"$promptSms\"")
        assertTrue(promptSms.contains("john", ignoreCase = true) && promptSms.contains("i will be late", ignoreCase = true))

        val confirmSms = coordinator.coordinate("confirm")
        assertTrue(confirmSms is ExecutionOutcome.Success)
        val smsTts = (confirmSms as ExecutionOutcome.Success).spokenResponse
        println("Confirmed Response TTS: \"$smsTts\"")
        assertTrue(smsTts.contains("john", ignoreCase = true))

        // 20. Confirmation Flow: WhatsApp
        println("\n--- Testing Confirmation Flow: WhatsApp ---")
        val waOutcome = coordinator.coordinate("whatsapp Alex see you soon")
        assertTrue(waOutcome is ExecutionOutcome.ConfirmationRequired)
        val promptWa = (waOutcome as ExecutionOutcome.ConfirmationRequired).prompt
        println("Prompt: \"$promptWa\"")
        assertTrue(promptWa.contains("alex", ignoreCase = true) && promptWa.contains("see you soon", ignoreCase = true))

        val cancelWa = coordinator.coordinate("no")
        assertTrue(cancelWa is ExecutionOutcome.Success)
        val cancelTts = (cancelWa as ExecutionOutcome.Success).spokenResponse
        println("Cancelled Response TTS: \"$cancelTts\"")
        assertEquals("Action cancelled, Sir.", cancelTts)

        // 21. Hinglish WhatsApp Flow
        println("\n--- Testing Hinglish WhatsApp Flow ---")
        val hinglishWa = resolver.resolve("mom ko whatsapp pe hello bhejo")
        assertTrue(hinglishWa is JarvisIntent.SendWhatsApp)
        assertEquals("mom", (hinglishWa as JarvisIntent.SendWhatsApp).contactName.lowercase())
        assertEquals("hello", (hinglishWa as JarvisIntent.SendWhatsApp).message.lowercase())

        val hinglishCall = resolver.resolve("papa ko call karo")
        assertTrue(hinglishCall is JarvisIntent.CallContact)
        assertEquals("papa", (hinglishCall as JarvisIntent.CallContact).contactName.lowercase())

        println("================================================================================")
        println("                     ALL 21 ACTIONS TESTED & VERIFIED OK                        ")
        println("================================================================================")
    }
}
