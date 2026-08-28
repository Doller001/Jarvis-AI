package com.jarvis.assistant.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class MicController(private val context: Context? = null) {

    companion object {
        private const val TAG = "MicController"
        const val OWNER_WAKE = "wake"
        const val OWNER_STT = "stt"
        const val OWNER_INTERRUPT = "interrupt"
    }

    private val lock = ReentrantLock()
    @Volatile private var currentOwner: String? = null
    @Volatile private var acquireCount: Int = 0

    fun hasPermission(): Boolean {
        val ctx = context ?: return false
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    fun acquireMic(owner: String): Boolean = lock.withLock {
        if (currentOwner == null || currentOwner == owner) {
            currentOwner = owner
            acquireCount++
            Log.d(TAG, "Mic acquired by $owner (count=$acquireCount)")
            true
        } else {
            Log.w(TAG, "Mic busy — held by $currentOwner, $owner cannot acquire")
            false
        }
    }

    fun releaseMic(owner: String): Boolean = lock.withLock {
        if (currentOwner == owner) {
            acquireCount = (acquireCount - 1).coerceAtLeast(0)
            if (acquireCount == 0) {
                currentOwner = null
                Log.d(TAG, "Mic released by $owner → free")
            } else {
                Log.d(TAG, "Mic released by $owner (count=$acquireCount)")
            }
            true
        } else {
            Log.w(TAG, "Release denied: $owner does not hold mic (holder=$currentOwner)")
            false
        }
    }

    /**
     * Atomic mic handoff between owners.
     * Returns true if transfer succeeded.
     */
    fun transferOwnership(from: String, to: String): Boolean = lock.withLock {
        if (currentOwner == from && acquireCount > 0) {
            currentOwner = to
            Log.d(TAG, "Mic transferred: $from → $to")
            true
        } else {
            Log.w(TAG, "Transfer denied: expected holder=$from, actual=$currentOwner")
            false
        }
    }

    /**
     * Steal mic from any owner. Used for interrupts.
     */
    fun forceAcquire(owner: String): Boolean = lock.withLock {
        val prev = currentOwner
        currentOwner = owner
        acquireCount = 1
        Log.w(TAG, "Mic force-acquired by $owner (was: $prev)")
        true
    }

    fun releaseAny(): Boolean = lock.withLock {
        val prev = currentOwner
        currentOwner = null
        acquireCount = 0
        if (prev != null) {
            Log.d(TAG, "Mic released (was: $prev) → free")
        }
        true
    }

    fun getCurrentOwner(): String? = lock.withLock {
        currentOwner
    }

    fun isAvailable(): Boolean = lock.withLock {
        currentOwner == null
    }

    fun isOwnedBy(owner: String): Boolean = lock.withLock {
        currentOwner == owner
    }
}
