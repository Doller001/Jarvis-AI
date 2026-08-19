package com.jarvis.assistant.brain

import android.util.Log

class JarvisBrain(
    private val parser: CommandParser = CommandParser(),
    private val planner: Planner = Planner(),
    private val responseGenerator: ResponseGenerator = ResponseGenerator()
) {
    fun processCommand(utterance: String): ExecutionPlan {
        Log.i("JarvisBrain", "Processing user command: '$utterance'")
        val intent = parser.parse(utterance)
        return planner.createPlan(intent)
    }

    fun formatResponse(intent: JarvisIntent, rawResult: String): String {
        return responseGenerator.generateSpokenResponse(intent, rawResult)
    }
}
