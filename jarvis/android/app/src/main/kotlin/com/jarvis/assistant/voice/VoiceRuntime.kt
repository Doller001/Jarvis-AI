package com.jarvis.assistant.voice

import android.util.Log

enum class VoiceState { IDLE, WAKE_DETECTED, LISTENING, PROCESSING, SPEAKING, ERROR }

class VoiceRuntime(
    private val wakeWordEngine: WakeWordEngine = WakeWordEngine(),
    private val vadEngine: VadEngine = VadEngine(),
    private val speechRecognizer: SpeechRecognizer = SpeechRecognizer(),
    private val ttsEngine: TextToSpeechEngine = TextToSpeechEngine(),
    private val audioManager: AudioManager = AudioManager()
) {
    var state: VoiceState = VoiceState.IDLE
        private set

    fun startRuntime(onCommandRecognized: (String) -> Unit = {}) {
        Log.i("VoiceRuntime", "Starting Jarvis always-ready voice runtime...")
        state = VoiceState.IDLE
        wakeWordEngine.startMonitoring { detectedPhrase ->
            onWakeDetected(detectedPhrase, onCommandRecognized)
        }
    }

    private fun onWakeDetected(phrase: String, onCommandRecognized: (String) -> Unit) {
        Log.i("VoiceRuntime", "Wake phrase detected: '$phrase'! Activating VAD + STT...")
        state = VoiceState.WAKE_DETECTED
        vadEngine.activate()
        state = VoiceState.LISTENING

        speechRecognizer.startListening { text ->
            Log.i("VoiceRuntime", "Speech recognized: '$text'")
            state = VoiceState.PROCESSING
            vadEngine.deactivate()
            speechRecognizer.stopListening()
            onCommandRecognized(text)
        }
    }

    fun speakResponse(text: String, onComplete: () -> Unit = {}) {
        state = VoiceState.SPEAKING
        ttsEngine.speak(text) {
            state = VoiceState.IDLE
            onComplete()
        }
    }

    fun stopRuntime() {
        wakeWordEngine.stopMonitoring()
        speechRecognizer.stopListening()
        vadEngine.deactivate()
        state = VoiceState.IDLE
    }
}
