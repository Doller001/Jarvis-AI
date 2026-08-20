package com.jarvis.assistant.voice

/**
 * A low-power, offline wake-word detector. Owns the microphone while active.
 * The command recognizer (SpeechRecognizer) must never share the mic with it.
 */
interface WakeWordDetector {
    fun start()
    fun stop()
    fun pause()
    fun resume()
    fun release()

    fun setListener(listener: WakeWordListener)

    /** False when the detector cannot run (model missing, key missing, unsupported). */
    fun isAvailable(): Boolean
}

interface WakeWordListener {
    fun onWakeWordDetected()
    fun onWakeWordError(error: Throwable)
}