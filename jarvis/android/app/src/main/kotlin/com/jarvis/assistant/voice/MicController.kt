package com.jarvis.assistant.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Single Mic Owner controller.
 * Enforces the architectural rule: ONE TIME = ONE MIC CAPTURE SYSTEM.
 * Prevents concurrent access conflicts between background AudioRecord and SpeechRecognizer.
 */
class MicController(private val context: Context?) {
    companion object {
        private const val TAG = "MicController"
    }

    private val lock = Any()
    @Volatile
    private var currentOwner: String? = null

    /**
     * Checks whether RECORD_AUDIO permission has been granted by the user.
     */
    fun hasPermission(): Boolean {
        val ctx = context ?: return false
        return ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Attempts to acquire exclusive microphone ownership for the given component.
     * Returns true if granted, false if already held by another component.
     */
    fun acquireMic(owner: String): Boolean {
        synchronized(lock) {
            if (currentOwner != null && currentOwner != owner) {
                Log.w(TAG, "Mic access DENIED for '$owner' — currently held by '$currentOwner'")
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
                Log.w(TAG, "Component '$owner' tried to release mic held by '$currentOwner'")
            }
        }
    }

    /**
     * Forcefully acquires microphone ownership for high-priority command sessions.
     */
    fun forceAcquire(owner: String): Boolean {
        synchronized(lock) {
            val prev = currentOwner
            if (prev != null && prev != owner) {
                Log.w(TAG, "Force acquiring mic for '$owner' from '$prev'")
            }
            currentOwner = owner
            VoiceDiagnostics.logMicState("FORCE ACQUIRED by '$owner'")
            return true
        }
    }

    /**
     * Unconditionally releases mic lock.
     */
    fun releaseAny() {
        synchronized(lock) {
            val prev = currentOwner
            currentOwner = null
            if (prev != null) {
                VoiceDiagnostics.logMicState("UNCONDITIONALLY RELEASED (was '$prev')")
            }
        }
    }

    /**
     * Checks if the microphone is currently free to be acquired.
     */
    fun isMicAvailable(): Boolean {
        synchronized(lock) {
            return currentOwner == null
        }
    }

    /**
     * Returns the name of the component currently holding the microphone lock.
     */
    fun getCurrentOwner(): String? = currentOwner
}
