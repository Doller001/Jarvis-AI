package com.jarvis.assistant.routines

import android.content.Context
import android.util.Log
import com.jarvis.assistant.device.AppController
import com.jarvis.assistant.device.MediaController
import com.jarvis.assistant.device.SystemController

/**
 * RoutineEngine — executes named preset multi-step routines (Movie mode, Morning routine,
 * Meeting mode, Night mode, etc.) by orchestrating existing device controllers.
 *
 * Each routine returns a spoken confirmation string summarising what was done.
 * Routines are fire-and-forget: steps execute synchronously in order and partial
 * failures are silently skipped so the user gets best-effort execution.
 */
class RoutineEngine(private val context: Context? = null) {

    companion object {
        private const val TAG = "RoutineEngine"

        /** Canonical routine name → list of canonical aliases */
        val ROUTINE_ALIASES: Map<String, List<String>> = mapOf(
            "morning"  to listOf("morning routine", "good morning routine", "subah wali routine", "morning mode", "wake up mode", "daily start", "subah start karo"),
            "night"    to listOf("night routine", "night mode", "good night", "raat wali", "raat mode", "sleep mode", "bed time"),
            "movie"    to listOf("movie mode", "movie time", "film mode", "cinema mode", "movie dekho", "movie chalao"),
            "meeting"  to listOf("meeting mode", "office mode", "work mode", "presentation mode", "focus mode", "silent professional"),
            "driving"  to listOf("driving mode", "car mode", "gaadi mode", "drive mode"),
            "gym"      to listOf("gym mode", "workout mode", "exercise mode", "fitness mode"),
            "reading"  to listOf("reading mode", "study mode", "padhai mode", "focus reading"),
        )
    }

    private val systemController = SystemController(context)
    private val appController    = AppController(context)
    private val mediaController  = MediaController(context)

    /**
     * Resolve a raw spoken routine name to the canonical key.
     * Returns null if no known routine matches.
     */
    fun resolveRoutineName(raw: String): String? {
        val t = raw.lowercase().trim()
        for ((canonical, aliases) in ROUTINE_ALIASES) {
            if (t == canonical || aliases.any { t.contains(it) }) return canonical
        }
        return null
    }

    /**
     * Execute a routine by canonical name.
     * Returns a human-readable spoken summary.
     */
    fun execute(routineName: String): String {
        Log.i(TAG, "Executing routine: $routineName")
        return when (routineName.lowercase()) {
            "morning" -> executeMorning()
            "night"   -> executeNight()
            "movie"   -> executeMovie()
            "meeting" -> executeMeeting()
            "driving" -> executeDriving()
            "gym"     -> executeGym()
            "reading" -> executeReading()
            else      -> "Unknown routine: $routineName"
        }
    }

    // ========= ROUTINE IMPLEMENTATIONS =========

    /**
     * Morning Routine:
     * Brightness 80%, Volume 50%, DND off, ringer normal, notify summary
     */
    private fun executeMorning(): String {
        val steps = mutableListOf<String>()
        if (systemController.setBrightness(80))  steps += "brightness set to 80%"
        if (systemController.setVolume(50))       steps += "volume set to 50%"
        if (systemController.setDnd(false))       steps += "Do Not Disturb disabled"
        if (systemController.setRingerMode("normal")) steps += "ringer set to normal"
        val time = systemController.getTime()
        val battery = systemController.getBatteryLevel()
        Log.i(TAG, "Morning routine complete: $steps")
        return buildString {
            append("Good morning, Sir! Morning routine activated. ")
            append(steps.joinToString(", ").replaceFirstChar { it.uppercase() })
            append(". Current time: $time. Battery: $battery.")
        }
    }

    /**
     * Night Routine:
     * Brightness 15%, Volume 20%, DND on, silent mode
     */
    private fun executeNight(): String {
        val steps = mutableListOf<String>()
        if (systemController.setBrightness(15))      steps += "brightness dimmed to 15%"
        if (systemController.setVolume(20))           steps += "volume reduced to 20%"
        if (systemController.setDnd(true))            steps += "Do Not Disturb enabled"
        if (systemController.setRingerMode("silent")) steps += "ringer silenced"
        Log.i(TAG, "Night routine complete: $steps")
        return buildString {
            append("Good night, Sir. Night routine activated. ")
            append(steps.joinToString(", ").replaceFirstChar { it.uppercase() })
            append(". Rest well. JARVIS will keep watch.")
        }
    }

    /**
     * Movie Mode:
     * Brightness 100%, Volume 80%, DND on, torch off
     */
    private fun executeMovie(): String {
        val steps = mutableListOf<String>()
        if (systemController.setBrightness(100)) steps += "brightness maxed"
        if (systemController.setVolume(80))      steps += "volume set to 80%"
        if (systemController.setDnd(true))       steps += "Do Not Disturb enabled"
        if (systemController.toggleTorch(false)) steps += "torch off"
        if (systemController.setRotationLock(false)) steps += "auto-rotate enabled"
        Log.i(TAG, "Movie routine complete: $steps")
        return buildString {
            append("Movie mode activated! ")
            append(steps.joinToString(", ").replaceFirstChar { it.uppercase() })
            append(". Enjoy the show, Sir!")
        }
    }

    /**
     * Meeting Mode:
     * DND on, vibrate, brightness 60%, volume off
     */
    private fun executeMeeting(): String {
        val steps = mutableListOf<String>()
        if (systemController.setDnd(true))             steps += "Do Not Disturb enabled"
        if (systemController.setRingerMode("vibrate")) steps += "ringer set to vibrate"
        if (systemController.setVolume(0))             steps += "volume muted"
        if (systemController.setBrightness(60))        steps += "brightness set to 60%"
        Log.i(TAG, "Meeting routine complete: $steps")
        return buildString {
            append("Meeting mode activated. ")
            append(steps.joinToString(", ").replaceFirstChar { it.uppercase() })
            append(". You're all set for a professional session, Sir.")
        }
    }

    /**
     * Driving Mode:
     * Volume 100%, DND on (allows calls), rotation lock off (landscape), brightness auto (max)
     */
    private fun executeDriving(): String {
        val steps = mutableListOf<String>()
        if (systemController.setVolume(100))         steps += "volume maxed for hands-free"
        if (systemController.setBrightness(100))     steps += "brightness maxed for visibility"
        if (systemController.setRotationLock(false)) steps += "auto-rotate enabled"
        // Open maps for navigation readiness
        val mapsOk = appController.launchApp("maps")
        if (mapsOk) steps += "Maps opened"
        Log.i(TAG, "Driving routine complete: $steps")
        return buildString {
            append("Driving mode activated. ")
            append(steps.joinToString(", ").replaceFirstChar { it.uppercase() })
            append(". Drive safe, Sir. JARVIS is co-pilot.")
        }
    }

    /**
     * Gym Mode:
     * Volume 100%, DND on, music play
     */
    private fun executeGym(): String {
        val steps = mutableListOf<String>()
        if (systemController.setVolume(100))  steps += "volume maxed"
        if (systemController.setDnd(true))    steps += "Do Not Disturb on"
        if (mediaController.playMedia())      steps += "music started"
        Log.i(TAG, "Gym routine complete: $steps")
        return buildString {
            append("Gym mode activated! ")
            append(steps.joinToString(", ").replaceFirstChar { it.uppercase() })
            append(". Let's crush it, Sir! You've got this.")
        }
    }

    /**
     * Reading / Study Mode:
     * Brightness 50%, DND on, silent, torch off
     */
    private fun executeReading(): String {
        val steps = mutableListOf<String>()
        if (systemController.setBrightness(50))      steps += "brightness set to 50% (comfortable reading)"
        if (systemController.setDnd(true))           steps += "Do Not Disturb enabled"
        if (systemController.setRingerMode("silent")) steps += "ringer silenced"
        if (systemController.setRotationLock(true))  steps += "rotation locked to portrait"
        Log.i(TAG, "Reading routine complete: $steps")
        return buildString {
            append("Reading mode activated. ")
            append(steps.joinToString(", ").replaceFirstChar { it.uppercase() })
            append(". Enjoy your reading session, Sir.")
        }
    }

    /**
     * Returns a spoken list of all available routines for "what routines can you run?" queries.
     */
    fun listRoutines(): String {
        return "Available routines: Morning, Night, Movie, Meeting, Driving, Gym, and Reading mode. " +
               "Say the routine name to activate it — for example, 'movie mode on karo' or 'meeting mode chalao'."
    }
}
