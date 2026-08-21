package com.jarvis.assistant.memory

import android.content.Context
import android.util.Log
import kotlin.math.min

enum class RouteSource {
    FAST_CAG_EXACT,
    FAST_CAG_NEAR,
    FAST_COMMAND,
    SLOW_RAG_LLM
}

data class RoutedAnswer(
    val text: String,
    val source: RouteSource,
    val complexity: Float,
    val ragContext: List<RagChunk> = emptyList(),
    val userFacts: List<MemoryFact> = emptyList()
)

/**
 * High-speed Decision & Router Engine (Fast/Slow Path Selector).
 * Implements:
 * 1. Sub-ms CAG Exact Lookup
 * 2. <5ms CAG Near/Similarity Lookup
 * 3. 0-1 Complexity Estimator
 * 4. RAG Knowledge & MAG Long-term User Context Gathering for Slow Path
 * 5. Learning Loop: Caches slow LLM responses into CAG/RAG/MAG for instant offline reuse.
 */
class MemoryDecisionRouter(
    private val context: Context,
    private val memoryEngine: MemoryEngine = MemoryEngine(context)
) {
    companion object {
        private const val TAG = "MemoryRouter"
        private const val CAG_THRESHOLD = 0.88f
        private const val FAST_THRESHOLD = 0.22f
    }

    fun route(input: String): RoutedAnswer {
        val qNorm = input.trim().lowercase()

        // 1. FAST CAG Exact Lookup (Sub-ms)
        memoryEngine.cagExactLookup(input)?.let {
            Log.i(TAG, "FAST CAG Exact Hit for '$qNorm'")
            return RoutedAnswer(it.answer, RouteSource.FAST_CAG_EXACT, 0.0f)
        }

        // 2. FAST CAG Near Lookup (< 5ms)
        memoryEngine.cagNearLookup(input)?.let {
            if (it.score >= CAG_THRESHOLD) {
                Log.i(TAG, "FAST CAG Near Hit for '$qNorm' (score: ${it.score})")
                return RoutedAnswer(it.answer, RouteSource.FAST_CAG_NEAR, 0.05f)
            }
        }

        // 3. Complexity Estimation
        val complexity = estimateComplexity(input)

        // 4. Fast Path for deterministic hardware & conversational rules
        if (complexity <= FAST_THRESHOLD) {
            return RoutedAnswer("", RouteSource.FAST_COMMAND, complexity)
        }

        // 5. SLOW PATH: Gather RAG Knowledge & MAG Long-term user memory
        val ragChunks = memoryEngine.ragSearch(input, topK = 4)
        val userFacts = memoryEngine.getAllFacts().take(4)

        return RoutedAnswer(
            text = "",
            source = RouteSource.SLOW_RAG_LLM,
            complexity = complexity,
            ragContext = ragChunks,
            userFacts = userFacts
        )
    }

    /**
     * Learning Loop: Ingests successful answers into CAG, RAG, and MAG so
     * subsequent identical or similar questions run locally in < 5ms without LLM.
     */
    fun learn(input: String, answer: String) {
        if (input.isBlank() || answer.isBlank()) return
        if (answer.contains("error", ignoreCase = true) || answer.length < 4) return

        // Instant CAG reuse
        memoryEngine.cagPut(input, answer)
        // Ingest into local knowledge base
        memoryEngine.ragIngest(answer, docId = "learned_qna", src = "user_qna")
        // Extract user facts / memory
        memoryEngine.extractAndStoreMemories(input, answer)
        Log.i(TAG, "Auto-learned Q&A into CAG/RAG/MAG: '$input' -> '${answer.take(30)}...'")
    }

    fun estimateComplexity(input: String): Float {
        val lower = input.lowercase().trim()
        var score = 0f

        // Fast intent zeroing (commands & conversational staples)
        if (lower.startsWith("torch") || lower.startsWith("flashlight") ||
            lower.startsWith("wifi") || lower.startsWith("bluetooth") ||
            lower.startsWith("open ") || lower.startsWith("close ") ||
            lower.startsWith("call ") || lower.startsWith("sms ") ||
            lower.startsWith("volume") || lower.contains("battery") ||
            lower.contains("storage") || lower.contains("time") ||
            lower in listOf("hello", "hi", "hey", "namaste", "who are you", "how are you", "thank you", "thanks", "bye")) {
            return 0.0f
        }

        // Knowledge keywords
        val knowledgeWords = listOf(
            "what is", "why", "how does", "explain", "code", "write", "history of",
            "solve", "calculate", "kya hota", "kaise banaye", "tell me about"
        )
        if (knowledgeWords.any { lower.contains(it) }) {
            score += 0.45f
        }

        // Length complexity
        score += min(input.length / 300f, 0.25f)

        // Question mark / query structure
        if (input.contains("?")) score += 0.15f

        return score.coerceIn(0f, 1f)
    }

    fun getEngine(): MemoryEngine = memoryEngine
}
