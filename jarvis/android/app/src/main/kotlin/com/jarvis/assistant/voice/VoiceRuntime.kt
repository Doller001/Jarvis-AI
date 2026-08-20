package com.jarvis.assistant.voice

import android.content.Context
import android.util.Log

enum class VoiceState { IDLE, WAKE_DETECTED, LISTENING, PROCESSING, SPEAKING, ERROR }

class VoiceRuntime(
    private val context: Context? = null,
    private val wakeWordEngine: WakeWordEngine = WakeWordEngine(context = context),
    private val ttsEngine: TextToSpeechEngine = TextToSpeechEngine(context = context)
) {
    var state: VoiceState = VoiceState.IDLE
        private set

    private var commandCallback: ((String) -> Unit)? = null

    fun startRuntime(onCommandRecognized: (String) -> Unit = {}) {
        Log.i("VoiceRuntime", "Starting Jarvis voice runtime...")
        state = VoiceState.IDLE
        commandCallback = onCommandRecognized
        wakeWordEngine.startMonitoring { fullText ->
            onWakeDetected(fullText, onCommandRecognized)
        }
    }

    private fun onWakeDetected(fullText: String, onCommandRecognized: (String) -> Unit) {
        Log.i("VoiceRuntime", "Wake phrase detected: '$fullText'")
        state = VoiceState.WAKE_DETECTED
        val command = wakeWordEngine.extractCommand(fullText)
        state = VoiceState.PROCESSING
        onCommandRecognized(command)
    }

    fun speakResponse(text: String, onComplete: () -> Unit = {}) {
        state = VoiceState.SPEAKING
        ttsEngine.speak(text) {
            state = VoiceState.IDLE
            onComplete()
        }
    }

    fun toggleMonitoring(): Boolean {
        if (wakeWordEngine.isMonitoring()) {
            stopRuntime()
            return false
        }
        wakeWordEngine.startMonitoring { fullText ->
            onWakeDetected(fullText, commandCallback ?: {})
        }
        state = VoiceState.IDLE
        return true
    }

    fun stopRuntime() {
        wakeWordEngine.stopMonitoring()
        state = VoiceState.IDLE
    }
}