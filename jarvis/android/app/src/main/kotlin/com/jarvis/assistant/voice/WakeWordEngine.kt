package com.jarvis.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer as AndroidSpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * Application-level coordinator for wake-word detection.
 *
 * Primary path: an offline [WakeWordDetector] (Porcupine) owns the mic at low
 * power and fires on "Hey Jarvis".
 *
 * Fallback path (used only when no offline detector is available, i.e. the
 * custom .ppn model / access key are missing): a continuously-running
 * Android SpeechRecognizer matches the phrase in recognized text. Kept so the
 * assistant keeps working without the model; clearly labeled in logs.
 *
 * Cooldown/debounce is applied centrally to both paths so a wake event can
 * never fire twice in quick succession.
 */
class WakeWordEngine(
    private val context: Context? = null,
    private val config: WakeWordConfig = WakeWordConfig(),
    private val detector: WakeWordDetector? = null
) {

    companion object {
        private const val TAG = "WakeWordEngine"
        private const val SILENCE_TIMEOUT_MS = 3000L
        private val phraseVariants = listOf(
            "hey jarvis", "hey, jarvis", "hay jarvis", "jarvis", "jarvis listen", "okay jarvis"
        )
    }

    private var isMonitoring = false
    private var onWakeCallback: ((String?) -> Unit)? = null
    private var errorCallback: ((Throwable) -> Unit)? = null

    private val cooldown = WakeCooldown(config.cooldownMs)

    private var fallbackRecognizer: AndroidSpeechRecognizer? = null
    private val restartHandler = Handler(Looper.getMainLooper())
    private var restartScheduled = false

    private val accumulated = StringBuilder()
    private var lastSpeechMs = 0L
    private val finalizeHandler = Handler(Looper.getMainLooper())
    private val finalizeRunnable = Runnable { maybeFinalize() }

    private val useFallback: Boolean = detector == null || !detector.isAvailable()

    init {
        if (useFallback) {
            Log.w(TAG, "No offline wake-word detector available — using fallback text matching")
        }
    }

    fun isMonitoring(): Boolean = isMonitoring

    fun usesFallback(): Boolean = useFallback

    fun startMonitoring(onWake: (String?) -> Unit, onError: (Throwable) -> Unit = {}) {
        onWakeCallback = onWake
        errorCallback = onError
        isMonitoring = true
        cooldown.reset()
        if (useFallback) {
            Log.i(TAG, "Fallback wake-word monitoring active (phrases: $phraseVariants)")
            startFinalizeWatchdog()
            listenOnce()
        } else {
            detector?.setListener(object : WakeWordListener {
                override fun onWakeWordDetected() {
                    if (!isMonitoring) return
                    Log.i(TAG, "Wake word detected")
                    if (allowWake()) {
                        onWakeCallback?.invoke(null)
                    }
                }

                override fun onWakeWordError(error: Throwable) {
                    Log.e(TAG, "Wake-word detector error", error)
                    errorCallback?.invoke(error)
                }
            })
            detector?.start()
            Log.i(TAG, "Listening for wake word")
        }
    }

    fun stopMonitoring() {
        isMonitoring = false
        stopFinalizeWatchdog()
        accumulated.setLength(0)
        detector?.stop()
        stopFallbackRecognizer()
        Log.i(TAG, "Stopped")
    }

    /** Hands the microphone to the command recognizer (or pauses the fallback loop). */
    fun pause() {
        if (!isMonitoring) return
        stopFinalizeWatchdog()
        accumulated.setLength(0)
        detector?.pause()
        stopFallbackRecognizer()
        Log.i(TAG, "Paused — command mode owns the microphone")
    }

    /** Returns the microphone to wake-word listening. */
    fun resume() {
        if (!isMonitoring) return
        if (useFallback) {
            startFinalizeWatchdog()
            listenOnce()
        } else {
            detector?.resume()
        }
        Log.i(TAG, "Resumed — wake-word listening")
    }

    fun release() {
        onWakeCallback = null
        isMonitoring = false
        stopFinalizeWatchdog()
        accumulated.setLength(0)
        detector?.release()
        stopFallbackRecognizer()
        Log.i(TAG, "Released")
    }

    /** Cooldown gate shared by both detection paths. */
    private fun allowWake(): Boolean = cooldown.allow()

    // ------------------------------------------------------------------
    // Fallback path: continuous STT + phrase matching
    // ------------------------------------------------------------------

    private fun listenOnce() {
        val ctx = context ?: run {
            Log.w(TAG, "Context not available — cannot listen for wake word")
            return
        }
        if (!isMonitoring) return
        try {
            if (fallbackRecognizer == null) {
                fallbackRecognizer = AndroidSpeechRecognizer.createSpeechRecognizer(ctx)
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                // Keep one session open as long as possible so continuous
                // listening never visibly re-arms while idle.
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 15_000)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 15_000)
            }
            fallbackRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    Log.e(TAG, "Fallback recognition error code: $error")
                    if (error == AndroidSpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        isMonitoring = false
                        errorCallback?.invoke(
                            IllegalStateException("Microphone permission missing (fallback mode)")
                        )
                        return
                    }
                    scheduleRestart(fallbackRestartDelayMs(error), recreate = shouldRecreateOnError(error))
                }

                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(AndroidSpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    if (text.isNotBlank()) {
                        accumulate(text)
                        lastSpeechMs = SystemClock.uptimeMillis()
                    }
                    // Seamless restart: keep the same recognizer so a user
                    // speaking through the gap never loses their words.
                    scheduleRestart(250L, recreate = false)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults?.getStringArrayList(AndroidSpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    if (text.isNotBlank()) {
                        accumulate(text)
                        lastSpeechMs = SystemClock.uptimeMillis()
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            fallbackRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start fallback recognizer", e)
        }
    }

    private fun stopFallbackRecognizer() {
        try {
            fallbackRecognizer?.stopListening()
            fallbackRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop fallback recognizer", e)
        }
        fallbackRecognizer = null
    }

    private fun startFinalizeWatchdog() {
        finalizeHandler.removeCallbacks(finalizeRunnable)
        finalizeHandler.postDelayed(finalizeRunnable, 500L)
    }

    private fun stopFinalizeWatchdog() {
        finalizeHandler.removeCallbacks(finalizeRunnable)
    }

    /**
     * Debounced restart: waits before listening again so a busy/stuck
     * recognizer can never hot-loop. Errors recreate the recognizer to clear
     * a stuck instance; results reuse it for a seamless mid-speech handover.
     */
    private fun scheduleRestart(delayMs: Long, recreate: Boolean) {
        if (!isMonitoring || restartScheduled) return
        restartScheduled = true
        restartHandler.postDelayed({
            restartScheduled = false
            if (isMonitoring) {
                if (recreate) stopFallbackRecognizer()
                listenOnce()
            }
        }, delayMs)
    }

    /** Longer backoff for a busy recognizer so it can settle. */
    internal fun fallbackRestartDelayMs(error: Int = -1): Long =
        if (error == AndroidSpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1200L else 500L

    /**
     * Silence outcomes (no match / speech timeout) just mean nobody talked:
     * reuse the recognizer so its VAD stays warm and the mic never visibly
     * re-arms. Real failures recreate to clear a stuck instance.
     */
    internal fun shouldRecreateOnError(error: Int): Boolean =
        error != AndroidSpeechRecognizer.ERROR_NO_MATCH &&
            error != AndroidSpeechRecognizer.ERROR_SPEECH_TIMEOUT

    /**
     * Appends a new recognition fragment to the in-progress utterance.
     * Handles the case where the recognizer restarts mid-speech: a fragment
     * that starts with what we already have only extends the buffer.
     */
    internal fun accumulate(newText: String): String {
        val existing = accumulated.toString()
        val fresh = newText.trim()
        if (fresh.isEmpty()) return existing
        accumulated.setLength(0)
        if (existing.isNotEmpty() && fresh.startsWith(existing)) {
            accumulated.append(fresh)
        } else if (existing.isEmpty() || existing.contains(fresh)) {
            accumulated.append(fresh)
        } else {
            accumulated.append(existing).append(' ').append(fresh)
        }
        Log.i(TAG, "Accumulated so far: '$accumulated'")
        return accumulated.toString()
    }

    /**
     * Fires the wake callback only once the user has stopped speaking for
     * [SILENCE_TIMEOUT_MS], so a long "Hey Jarvis, …" never gets cut short.
     */
    private fun maybeFinalize() {
        if (!isMonitoring) return
        if (accumulated.isNotBlank() && SystemClock.uptimeMillis() - lastSpeechMs >= SILENCE_TIMEOUT_MS) {
            val text = accumulated.toString()
            accumulated.setLength(0)
            Log.i(TAG, "Finalized utterance: '$text'")
            if (isWakePhraseMatch(text)) {
                onWakeCallback?.invoke(text)
            }
        }
        finalizeHandler.postDelayed(finalizeRunnable, 500L)
    }

    /** Public for manual command mode and tests. */
    fun isWakePhraseMatch(text: String): Boolean {
        if (!allowWake()) return false
        val cleaned = text.lowercase().strip()
        return phraseVariants.any { cleaned.contains(it) }
    }

    fun extractCommand(fullText: String): String {
        var command = fullText.lowercase()
        for (variant in phraseVariants) {
            command = command.replace(variant.lowercase(), " ")
        }
        return command.trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
}