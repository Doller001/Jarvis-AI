package com.jarvis.assistant.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class MicController(
    private val context: Context? = null
) {

    companion object {
        private const val TAG = "MicController"

        const val OWNER_WAKE = "wake"
        const val OWNER_STT = "stt"
        const val OWNER_INTERRUPT = "interrupt"
    }

    private val lock = ReentrantLock()

    @Volatile
    private var currentOwner: String? = null

    @Volatile
    private var acquireCount: Int = 0

    fun hasPermission(): Boolean {
        val ctx = context ?: return false

        return ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun acquireMic(owner: String): Boolean = lock.withLock {
        requireValidOwner(owner)

        when {
            currentOwner == null -> {
                currentOwner = owner
                acquireCount = 1

                Log.d(
                    TAG,
                    "MIC_ACQUIRE owner=$owner count=$acquireCount"
                )

                true
            }

            currentOwner == owner -> {
                acquireCount++

                Log.d(
                    TAG,
                    "MIC_REACQUIRE owner=$owner count=$acquireCount"
                )

                true
            }

            else -> {
                Log.w(
                    TAG,
                    "MIC_BUSY requested=$owner current=$currentOwner"
                )

                false
            }
        }
    }

    fun releaseMic(owner: String): Boolean = lock.withLock {
        requireValidOwner(owner)

        if (currentOwner != owner) {
            Log.w(
                TAG,
                "MIC_RELEASE_REJECT owner=$owner holder=$currentOwner"
            )
            return@withLock false
        }

        acquireCount = (acquireCount - 1).coerceAtLeast(0)

        if (acquireCount == 0) {
            currentOwner = null

            Log.d(
                TAG,
                "MIC_RELEASE owner=$owner -> FREE"
            )
        } else {
            Log.d(
                TAG,
                "MIC_RELEASE owner=$owner remaining=$acquireCount"
            )
        }

        true
    }

    /**
     * Explicit ownership transfer.
     *
     * IMPORTANT:
     * This only changes logical ownership.
     * The caller must ensure that the previous physical audio resource
     * has been fully stopped/released before using this method.
     */
    fun transferOwnership(
        from: String,
        to: String
    ): Boolean = lock.withLock {

        requireValidOwner(from)
        requireValidOwner(to)

        if (currentOwner != from || acquireCount <= 0) {
            Log.w(
                TAG,
                "MIC_TRANSFER_REJECT from=$from to=$to holder=$currentOwner"
            )
            return@withLock false
        }

        currentOwner = to
        acquireCount = 1

        Log.i(
            TAG,
            "MIC_TRANSFER $from -> $to"
        )

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

    fun getAcquireCount(): Int = lock.withLock {
        acquireCount
    }

    private fun requireValidOwner(owner: String) {
        require(
            owner == OWNER_WAKE ||
                    owner == OWNER_STT ||
                    owner == OWNER_INTERRUPT
        ) {
            "Unknown microphone owner: $owner"
        }
    }
}

