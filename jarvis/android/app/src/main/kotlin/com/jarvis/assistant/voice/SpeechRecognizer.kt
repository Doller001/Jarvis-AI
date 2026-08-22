package com.jarvis.assistant.voice

import android.content.Context

/**
 * Compatibility wrapper for [SpeechController].
 * Preserves existing signatures while providing Single Mic Owner locking and full diagnostics.
 */
class SpeechRecognizer(
    private val context: Context? = null,
    micController: MicController = MicController(context)
) {
    private val controller = SpeechController(context, micController)

    fun startListening(
        onResult: (String) -> Unit,
        onError: (Int) -> Unit
    ) {
        controller.startListening(
            onResult = onResult,
            onError = { code, _ -> onError(code) }
        )
    }

    fun startListeningWithDiagnostics(
        onResult: (String) -> Unit,
        onError: (errorCode: Int, errorMessage: String) -> Unit,
        onRmsChanged: ((Float) -> Unit)? = null
    ) {
        controller.startListening(onResult, onError, onRmsChanged)
    }

    fun stopListening() {
        controller.stopListening()
    }

    fun destroy() {
        controller.destroy()
    }
}