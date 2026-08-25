package com.jarvis.assistant.voice

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.jarvis.assistant.telemetry.DiagnosticEventBus
import com.jarvis.assistant.telemetry.TelemetryEventType

data class AudioSnapshot(
    val mediaVolume: Int,
    val audioMode: Int,
    val isBluetoothScoOn: Boolean,
    val isSpeakerphoneOn: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Single authoritative owner for audio session snapshots, routing, and volume preservation.
 *
 * Guarantees:
 *  1. Never alters user's media stream volume during speech recognition or background listening.
 *  2. Preserves exact audio mode and restores it immediately upon session completion.
 *  3. Verifies before/after volume invariants to detect uncommanded volume suppressions.
 */
class AudioSessionManager(
    private val context: Context?,
    private val audioRouteManager: AudioRouteManager = AudioRouteManager(context)
) {
    companion object {
        private const val TAG = "AudioSessionManager"
    }

    private val audioManager: AudioManager? = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var activeSnapshot: AudioSnapshot? = null

    fun start() {
        audioRouteManager.start()
    }

    /**
     * Snapshots the audio state before entering command listening or speech capture.
     */
    fun beginSession(): AudioSnapshot? {
        val am = audioManager ?: return null
        val snapshot = AudioSnapshot(
            mediaVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC),
            audioMode = am.mode,
            isBluetoothScoOn = am.isBluetoothScoOn,
            isSpeakerphoneOn = am.isSpeakerphoneOn
        )
        activeSnapshot = snapshot

        DiagnosticEventBus.emit(
            type = TelemetryEventType.AUDIO_SESSION_START,
            component = TAG,
            details = mapOf(
                "mediaVolume" to snapshot.mediaVolume,
                "audioMode" to snapshot.audioMode,
                "isBluetoothScoOn" to snapshot.isBluetoothScoOn
            )
        )

        audioRouteManager.activateVoiceRouting()
        return snapshot
    }

    /**
     * Restores audio state and verifies the volume invariant.
     */
    fun endSession(expectedVolumeChange: Boolean = false) {
        audioRouteManager.deactivateVoiceRouting()
        val am = audioManager ?: return
        val snapshot = activeSnapshot ?: return

        val mediaVolumeAfter = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val isInvariantPreserved = expectedVolumeChange || (snapshot.mediaVolume == mediaVolumeAfter)

        if (!isInvariantPreserved) {
            Log.w(TAG, "Volume invariant violated! Before=${snapshot.mediaVolume}, After=$mediaVolumeAfter. Restoring original volume.")
            try {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, snapshot.mediaVolume, 0)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore original media volume", e)
            }
        }

        // Restore audio mode
        if (am.mode != snapshot.audioMode) {
            try {
                am.mode = snapshot.audioMode
            } catch (e: Exception) {
                Log.w(TAG, "Failed to restore audio mode ${snapshot.audioMode}", e)
            }
        }

        DiagnosticEventBus.emit(
            type = TelemetryEventType.AUDIO_SESSION_END,
            component = TAG,
            success = isInvariantPreserved,
            details = mapOf(
                "volumeBefore" to snapshot.mediaVolume,
                "volumeAfter" to mediaVolumeAfter,
                "invariantPassed" to isInvariantPreserved
            )
        )

        activeSnapshot = null
    }

    fun release() {
        endSession()
        audioRouteManager.release()
    }
}
