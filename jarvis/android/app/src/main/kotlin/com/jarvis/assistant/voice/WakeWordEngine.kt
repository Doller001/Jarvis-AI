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
 * Fallback path (used when no offline detector is available): Android SpeechRecognizer
 * matches the phrase in recognized speech. Carefully debounced and lifecycle-managed
 * so it never enters a rapid continuous restarting loop.
 */
class WakeWordEngine(
    private val context: Context? = null,
    private val config: WakeWordConfig = WakeWordConfig(),
    private val detector: WakeWordDetector? = null
) {

    companion object {
        private const val TAG = "WakeWordEngine"
        private val phraseVariants = listOf(
            "hey jarvis", "hey, jarvis", "hay jarvis", "jarvis", "jarvis listen", "okay jarvis",
            "jarvis suno", "jarvis hello", "suno jarvis", "hello jarvis"
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
            startListeningLoop()
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
        cancelScheduledRestart()
        accumulated.setLength(0)
        detector?.stop()
        stopFallbackRecognizer()
        Log.i(TAG, "Stopped")
    }

    /** Hands the microphone to the command recognizer (or pauses the fallback loop). */
    fun pause() {
        if (!isMonitoring) return
        cancelScheduledRestart()
        accumulated.setLength(0)
        detector?.pause()
        stopFallbackRecognizer()
        Log.i(TAG, "Paused — command mode owns the microphone")
    }

    /** Returns the microphone to wake-word listening. */
    fun resume() {
        if (!isMonitoring) return
        if (useFallback) {
            startListeningLoop()
        } else {
            detector?.resume()
        }
        Log.i(TAG, "Resumed — wake-word listening")
    }

    fun release() {
        onWakeCallback = null
        isMonitoring = false
        cancelScheduledRestart()
        accumulated.setLength(0)
        detector?.release()
        stopFallbackRecognizer()
        Log.i(TAG, "Released")
    }

    /** Cooldown gate shared by both detection paths. */
    private fun allowWake(): Boolean = cooldown.allow()

    // ------------------------------------------------------------------
    // Fallback path: safe STT + phrase matching
    // ------------------------------------------------------------------

    private fun startListeningLoop() {
        cancelScheduledRestart()
        listenOnce()
    }

    private fun listenOnce() {
        val ctx = context ?: run {
            Log.w(TAG, "Context not available — cannot listen for wake word")
            return
        }
        if (!isMonitoring) return

        restartHandler.post {
            if (!isMonitoring) return@post
            try {
                stopFallbackRecognizer()
                fallbackRecognizer = AndroidSpeechRecognizer.createSpeechRecognizer(ctx)

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
                }

                fallbackRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        Log.d(TAG, "Fallback recognition event code: $error")
                        if (error == AndroidSpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                            isMonitoring = false
                            errorCallback?.invoke(
                                IllegalStateException("Microphone permission missing (fallback mode)")
                            )
                            return
                        }
                        if (isMonitoring) {
                            scheduleRestart(fallbackRestartDelayMs(error), recreate = shouldRecreateOnError(error))
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val text = results?.getStringArrayList(AndroidSpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull { it.isNotBlank() }.orEmpty()

                        if (text.isNotBlank()) {
                            accumulate(text)
                            lastSpeechMs = SystemClock.uptimeMillis()
                            val currentText = accumulated.toString()

                            if (isWakePhraseMatch(currentText)) {
                                accumulated.setLength(0)
                                stopFallbackRecognizer()
                                if (allowWake()) {
                                    onWakeCallback?.invoke(currentText)
                                }
                                return
                            }
                        }

                        if (isMonitoring) {
                            scheduleRestart(800L, recreate = false)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val text = partialResults?.getStringArrayList(AndroidSpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull { it.isNotBlank() }.orEmpty()

                        if (text.isNotBlank()) {
                            accumulate(text)
                            lastSpeechMs = SystemClock.uptimeMillis()
                            val currentText = accumulated.toString()

                            if (isWakePhraseMatch(currentText)) {
                                accumulated.setLength(0)
                                stopFallbackRecognizer()
                                if (allowWake()) {
                                    onWakeCallback?.invoke(currentText)
                                }
                            }
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                fallbackRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start fallback recognizer", e)
                if (isMonitoring) {
                    scheduleRestart(1500L, recreate = true)
                }
            }
        }
    }

    private fun stopFallbackRecognizer() {
        try {
            fallbackRecognizer?.cancel()
            fallbackRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop fallback recognizer", e)
        }
        fallbackRecognizer = null
    }

    private fun cancelScheduledRestart() {
        restartScheduled = false
        restartHandler.removeCallbacksAndMessages(null)
    }

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

    /** Backoff delay for recognizer resets so it does not rapidly cycle. */
    internal fun fallbackRestartDelayMs(error: Int = -1): Long = when (error) {
        AndroidSpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1500L
        AndroidSpeechRecognizer.ERROR_CLIENT -> 1200L
        AndroidSpeechRecognizer.ERROR_NO_MATCH,
        AndroidSpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 800L
        else -> 800L
    }

    internal fun shouldRecreateOnError(error: Int): Boolean =
        error != AndroidSpeechRecognizer.ERROR_NO_MATCH &&
            error != AndroidSpeechRecognizer.ERROR_SPEECH_TIMEOUT

    /**
     * Appends a new recognition fragment to the in-progress utterance.
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
        Log.d(TAG, "Accumulated utterance: '$accumulated'")
        return accumulated.toString()
    }

    /** Public for manual command mode and tests. */
    fun isWakePhraseMatch(text: String): Boolean {
        if (!allowWake()) return false
        val cleaned = text.lowercase().trim()
        return phraseVariants.any { variant ->
            cleaned.contains(variant)
        }
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