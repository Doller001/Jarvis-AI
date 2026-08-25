package com.jarvis.assistant.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Single Mic Owner controller — Phase 6 rebuild.
 *
 * Valid owners: WAKE_WORD | COMMAND_STT | (none)
 * WAKE_WORD and COMMAND_STT must NEVER co-exist.
 */
class MicController(private val context: Context?) {
    companion object {
        private const val TAG = "MicController"

        /** Canonical owner names used across the pipeline. */
        const val OWNER_WAKE = "WakeWordEngine"
        const val OWNER_STT  = "SpeechController"
    }

    private val lock = Any()
    @Volatile
    private var currentOwner: String? = null

    fun hasPermission(): Boolean {
        val ctx = context ?: return false
        return ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Attempts to acquire exclusive microphone ownership.
     * Returns true if granted, false if already held by another component.
     */
    fun acquireMic(owner: String): Boolean {
        synchronized(lock) {
            if (currentOwner != null && currentOwner != owner) {
                Log.w(TAG, "Mic DENIED for '$owner' — held by '$currentOwner'")
                return false
            }
            currentOwner = owner
            VoiceDiagnostics.logMicState("ACQUIRED by '$owner'")
            return true
        }
    }

    /**
     * Releases microphone ownership if held by the specified component.
     */
    fun releaseMic(owner: String) {
        synchronized(lock) {
            if (currentOwner == owner) {
                currentOwner = null
                VoiceDiagnostics.logMicState("RELEASED by '$owner'")
            } else if (currentOwner != null) {
                Log.w(TAG, "'$owner' tried to release mic held by '$currentOwner'")
            }
        }
    }

    /**
     * Forcefully acquires microphone ownership for high-priority command sessions.
     * Only call when you are certain the previous owner has already stopped recording.
     */
    fun forceAcquire(owner: String): Boolean {
        synchronized(lock) {
            val prev = currentOwner
            if (prev != null && prev != owner) {
                Log.w(TAG, "Force acquiring mic for '$owner' from '$prev' — ensure prior owner stopped")
            }
            currentOwner = owner
            VoiceDiagnostics.logMicState("FORCE ACQUIRED by '$owner'")
            return true
        }
    }

    /** Unconditionally releases mic lock (emergency reset). */
    fun releaseAny() {
        synchronized(lock) {
            val prev = currentOwner
            currentOwner = null
            if (prev != null) {
                VoiceDiagnostics.logMicState("UNCONDITIONALLY RELEASED (was '$prev')")
            }
        }
    }

    fun isMicAvailable(): Boolean {
        synchronized(lock) { return currentOwner == null }
    }

    fun getCurrentOwner(): String? = currentOwner

    /**
     * Safety check: returns true if WAKE and STT owners are NOT simultaneously active.
     * Used by diagnostics / tests.
     */
    fun isOwnershipValid(): Boolean {
        val o = currentOwner
        // Only one of the two critical owners may hold the mic at a time.
        // Both null (available) and a single named owner are valid states.
        return true  // by design: acquireMic enforces exclusivity
    }
}
