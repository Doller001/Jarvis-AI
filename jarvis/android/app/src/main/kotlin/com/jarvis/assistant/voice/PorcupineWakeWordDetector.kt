package com.jarvis.assistant.voice

import android.content.Context
import android.util.Log
import ai.picovoice.porcupine.PorcupineManager
import com.jarvis.assistant.BuildConfig

/**
 * Offline wake-word detector backed by Picovoice Porcupine.
 *
 * Requires a custom "Hey Jarvis" keyword file (from the Picovoice Console,
 * https://console.picovoice.ai) at assets/hey-jarvis_en_android_v3_0_0.ppn
 * and a valid AccessKey in BuildConfig.JARVIS_PICOVOICE_ACCESS_KEY
 * (set via buildConfigField in app/build.gradle.kts).
 *
 * Until both are present, [isAvailable] returns false and the app runs in
 * fallback text-matching mode. PorcupineManager owns the microphone while
 * started; pause()/resume() transfer ownership cleanly.
 */
class PorcupineWakeWordDetector(
    private val context: Context,
    private val config: WakeWordConfig
) : WakeWordDetector {

    companion object {
        private const val TAG = "PorcupineDetector"
        private const val KEYWORD_ASSET = "hey-jarvis_en_android_v3_0_0.ppn"
    }

    private var listener: WakeWordListener? = null
    private var porcupine: PorcupineManager? = null
    private var running = false

    override fun isAvailable(): Boolean {
        if (BuildConfig.JARVIS_PICOVOICE_ACCESS_KEY.isBlank()) return false
        return try {
            context.assets.open(KEYWORD_ASSET).close()
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun setListener(listener: WakeWordListener) {
        this.listener = listener
    }

    override fun start() {
        if (running) return
        if (!isAvailable()) {
            listener?.onWakeWordError(
                IllegalStateException("Wake-word model or access key missing")
            )
            return
        }
        try {
            porcupine = PorcupineManager.Builder()
                .setAccessKey(BuildConfig.JARVIS_PICOVOICE_ACCESS_KEY)
                .setKeywordPaths(arrayOf("file:///android_asset/$KEYWORD_ASSET"))
                .setSensitivities(floatArrayOf(config.sensitivity))
                .build(context) { _ ->
                    Log.i(TAG, "Wake word detected")
                    listener?.onWakeWordDetected()
                }
            porcupine?.start()
            running = true
            Log.i(TAG, "Listening for wake word (Porcupine, sensitivity ${config.sensitivity})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start wake-word detector", e)
            running = false
            listener?.onWakeWordError(e)
        }
    }

    override fun stop() {
        if (!running && porcupine == null) return
        running = false
        try {
            porcupine?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping wake-word detector", e)
        }
        Log.i(TAG, "Stopped")
    }

    override fun pause() {
        if (!running) return
        running = false
        try {
            porcupine?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing wake-word detector", e)
        }
        Log.i(TAG, "Paused (mic handed to command recognizer)")
    }

    override fun resume() {
        if (running) return
        val pm = porcupine ?: run {
            start()
            return
        }
        try {
            pm.start()
            running = true
            Log.i(TAG, "Resumed (mic back from command recognizer)")
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming wake-word detector", e)
            listener?.onWakeWordError(e)
        }
    }

    override fun release() {
        listener = null
        running = false
        try {
            porcupine?.stop()
            porcupine?.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake-word detector", e)
        }
        porcupine = null
        Log.i(TAG, "Released")
    }
}