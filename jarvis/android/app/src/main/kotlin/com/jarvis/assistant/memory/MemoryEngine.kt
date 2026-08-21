package com.jarvis.assistant.memory

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

data class CagResult(val answer: String, val score: Float, val hits: Int)
data class RagChunk(val docId: String, val chunkIdx: Int, val text: String, val src: String)
data class MemoryFact(val id: Long, val type: String, val text: String, val confidence: Float, val timestamp: Long)

/**
 * Three-Tier Unified Memory Engine for JARVIS:
 * 1. CAG (Cache Augmented Generation): Sub-millisecond answer cache for exact & near-match queries.
 * 2. RAG (Retrieval Augmented Generation): Multi-chunk local knowledge retrieval.
 * 3. MAG (Memory Augmented Generation): Long-term user facts, preferences & episodic history.
 */
class MemoryEngine(private val context: Context) {

    companion object {
        private const val TAG = "MemoryEngine"
        private const val CAG_NEAR_THRESHOLD = 0.88f
    }

    private val dbHelper = JarvisMemoryDatabase(context)
    private val db: SQLiteDatabase get() = dbHelper.writableDatabase

    // In-memory hot cache for zero-latency lookups
    private val hotCagCache = ConcurrentHashMap<String, String>()

    init {
        warmUpHotCache()
    }

    private fun warmUpHotCache() {
        try {
            val cursor = db.rawQuery(
                "SELECT q_hash, answer FROM cag_cache ORDER BY hits DESC, last_used DESC LIMIT 100",
                null
            )
            cursor.use {
                while (it.moveToNext()) {
                    val hash = it.getString(0)
                    val ans = it.getString(1)
                    hotCagCache[hash] = ans
                }
            }
            Log.i(TAG, "Hot CAG cache initialized with ${hotCagCache.size} entries.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to warm up hot cache: ${e.message}")
        }
    }

    // =========================================================================
    // 1. CAG (Cache Augmented Generation) - Fast Path
    // =========================================================================

    fun cagExactLookup(query: String): CagResult? {
        val qNorm = query.trim().lowercase()
        val qHash = JarvisMemoryDatabase.sha256(qNorm)

        // Check RAM hot cache first (< 0.1 ms)
        hotCagCache[qHash]?.let {
            touchCag(qHash)
            return CagResult(it, 1.0f, 10)
        }

        // Check SQLite
        return try {
            val cursor = db.rawQuery(
                "SELECT answer, hits FROM cag_cache WHERE q_hash = ? LIMIT 1",
                arrayOf(qHash)
            )
            cursor.use {
                if (it.moveToFirst()) {
                    val answer = it.getString(0)
                    val hits = it.getInt(1)
                    hotCagCache[qHash] = answer
                    touchCag(qHash)
                    CagResult(answer, 1.0f, hits)
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "CAG exact lookup failed", e)
            null
        }
    }

    fun cagNearLookup(query: String): CagResult? {
        val qNorm = query.trim().lowercase()
        val tokens = qNorm.split(" ").filter { it.length > 2 }
        if (tokens.isEmpty()) return null

        return try {
            val cursor = db.rawQuery(
                "SELECT q_norm, q_hash, answer, hits FROM cag_cache ORDER BY hits DESC LIMIT 150",
                null
            )
            var bestScore = 0f
            var bestResult: CagResult? = null
            var bestHash: String? = null

            cursor.use {
                while (it.moveToNext()) {
                    val storedNorm = it.getString(0)
                    val hash = it.getString(1)
                    val ans = it.getString(2)
                    val hits = it.getInt(3)

                    val similarity = computeTokenSimilarity(qNorm, storedNorm)
                    if (similarity > bestScore) {
                        bestScore = similarity
                        bestResult = CagResult(ans, similarity, hits)
                        bestHash = hash
                    }
                }
            }

            if (bestScore >= CAG_NEAR_THRESHOLD && bestResult != null) {
                bestHash?.let { touchCag(it) }
                bestResult
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "CAG near lookup failed", e)
            null
        }
    }

    fun cagPut(query: String, answer: String) {
        val qNorm = query.trim().lowercase()
        if (qNorm.isBlank() || answer.isBlank()) return
        val qHash = JarvisMemoryDatabase.sha256(qNorm)
        val now = System.currentTimeMillis()

        hotCagCache[qHash] = answer

        try {
            val cv = ContentValues().apply {
                put("q_norm", qNorm)
                put("q_hash", qHash)
                put("answer", answer)
                put("hits", 1)
                put("last_used", now)
                put("created", now)
            }
            db.insertWithOnConflict("cag_cache", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            dbHelper.writeMetaJson()
            Log.i(TAG, "Learned new CAG entry for: '$qNorm'")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to put CAG entry", e)
        }
    }

    private fun touchCag(qHash: String) {
        try {
            db.execSQL(
                "UPDATE cag_cache SET hits = hits + 1, last_used = ? WHERE q_hash = ?",
                arrayOf(System.currentTimeMillis(), qHash)
            )
        } catch (_: Exception) {}
    }

    // =========================================================================
    // 2. RAG (Retrieval Augmented Generation) - Knowledge Store
    // =========================================================================

    fun ragSearch(query: String, topK: Int = 4): List<RagChunk> {
        val qTokens = query.trim().lowercase().split(" ").filter { it.length > 2 }
        if (qTokens.isEmpty()) return emptyList()

        return try {
            val cursor = db.rawQuery(
                "SELECT doc_id, chunk_idx, text, src FROM rag_chunks ORDER BY id DESC LIMIT 50",
                null
            )
            val list = mutableListOf<Pair<RagChunk, Float>>()

            cursor.use {
                while (it.moveToNext()) {
                    val docId = it.getString(0)
                    val idx = it.getInt(1)
                    val text = it.getString(2)
                    val src = it.getString(3) ?: "local"
                    val chunk = RagChunk(docId, idx, text, src)

                    val score = computeTokenSimilarity(query.lowercase(), text.lowercase())
                    if (score > 0.15f) {
                        list.add(Pair(chunk, score))
                    }
                }
            }
            list.sortedByDescending { it.second }.take(topK).map { it.first }
        } catch (e: Exception) {
            Log.e(TAG, "RAG search failed", e)
            emptyList()
        }
    }

    fun ragIngest(text: String, docId: String = "learned", src: String = "learned_engine") {
        if (text.isBlank()) return
        try {
            val cv = ContentValues().apply {
                put("doc_id", docId)
                put("chunk_idx", 0)
                put("text", text)
                put("src", src)
                put("ts", System.currentTimeMillis())
            }
            db.insert("rag_chunks", null, cv)
            Log.i(TAG, "Ingested knowledge chunk into RAG: '${text.take(40)}...'")
        } catch (e: Exception) {
            Log.e(TAG, "RAG ingest failed", e)
        }
    }

    // =========================================================================
    // 3. MAG (Memory Augmented Generation) - Facts, Profile, History
    // =========================================================================

    fun recordEpisode(role: String, text: String) {
        if (text.isBlank()) return
        try {
            val cv = ContentValues().apply {
                put("ts", System.currentTimeMillis())
                put("role", role)
                put("text", text)
            }
            db.insert("mem_episodes", null, cv)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to record episode: ${e.message}")
        }
    }

    fun getRecentEpisodes(limit: Int = 20): List<MessageLog> {
        val list = mutableListOf<MessageLog>()
        try {
            val cursor = db.rawQuery(
                "SELECT role, text, ts FROM mem_episodes ORDER BY id DESC LIMIT ?",
                arrayOf(limit.toString())
            )
            cursor.use {
                while (it.moveToNext()) {
                    list.add(MessageLog(it.getString(0), it.getString(1), it.getLong(2)))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get episodes: ${e.message}")
        }
        return list.reversed()
    }

    fun getAllFacts(): List<MemoryFact> {
        val list = mutableListOf<MemoryFact>()
        try {
            val cursor = db.rawQuery("SELECT id, type, text, confidence, ts FROM mem_facts ORDER BY id DESC LIMIT 50", null)
            cursor.use {
                while (it.moveToNext()) {
                    list.add(MemoryFact(it.getLong(0), it.getString(1), it.getString(2), it.getFloat(3), it.getLong(4)))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get facts: ${e.message}")
        }
        return list
    }

    fun storeFact(type: String, text: String, confidence: Float = 0.9f) {
        val now = System.currentTimeMillis()
        try {
            val cv = ContentValues().apply {
                put("type", type)
                put("text", text)
                put("confidence", confidence)
                put("ts", now)
                put("last_used", now)
            }
            db.insert("mem_facts", null, cv)
            Log.i(TAG, "Stored MAG fact: '$text'")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to store fact: ${e.message}")
        }
    }

    fun extractAndStoreMemories(userInput: String, assistantAnswer: String) {
        val input = userInput.trim()
        val lower = input.lowercase()

        // User profile statements
        if (lower.startsWith("my name is ") || lower.startsWith("mera naam ")) {
            val name = input.replace(Regex("(?i)^(my name is|mera naam)\\s+"), "").replace(".", "").trim()
            storeFact("user_pref", "User's name is $name", 1.0f)
            setUserProfile("owner_name", name)
        } else if (lower.startsWith("i live in ") || lower.startsWith("main rehta hoon ")) {
            val city = input.replace(Regex("(?i)^(i live in|main rehta hoon)\\s+"), "").replace(".", "").trim()
            storeFact("user_pref", "User lives in $city", 0.95f)
        } else if (lower.contains("remember that ") || lower.contains("yaad rakhna ki ")) {
            val fact = input.replace(Regex("(?i)^.*(remember that|yaad rakhna ki)\\s+"), "").trim()
            storeFact("semantic", fact, 0.95f)
        }
    }

    fun getUserProfile(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val cursor = db.rawQuery("SELECT key, value FROM mem_user", null)
            cursor.use {
                while (it.moveToNext()) {
                    map[it.getString(0)] = it.getString(1)
                }
            }
        } catch (_: Exception) {}
        return map
    }

    fun setUserProfile(key: String, value: String) {
        try {
            val cv = ContentValues().apply {
                put("key", key)
                put("value", value)
                put("ts", System.currentTimeMillis())
            }
            db.insertWithOnConflict("mem_user", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (_: Exception) {}
    }

    fun clearAllHistory() {
        hotCagCache.clear()
        try {
            db.execSQL("DELETE FROM mem_episodes")
            warmUpHotCache()
        } catch (_: Exception) {}
    }

    fun deleteEpisode(timestamp: Long) {
        try {
            db.execSQL("DELETE FROM mem_episodes WHERE ts = ?", arrayOf(timestamp))
        } catch (_: Exception) {}
    }

    // =========================================================================
    // Similarity Metrics
    // =========================================================================

    private fun computeTokenSimilarity(s1: String, s2: String): Float {
        if (s1 == s2) return 1.0f
        val words1 = s1.split(" ").filter { it.length > 1 }.toSet()
        val words2 = s2.split(" ").filter { it.length > 1 }.toSet()
        if (words1.isEmpty() || words2.isEmpty()) return 0f

        val intersection = words1.intersect(words2).size.toFloat()
        val union = words1.union(words2).size.toFloat()
        val jaccard = if (union > 0) intersection / union else 0f

        val maxLen = max(s1.length, s2.length)
        val editDist = levenshtein(s1, s2)
        val editSim = 1.0f - (editDist.toFloat() / maxLen)

        return (jaccard * 0.6f) + (editSim * 0.4f)
    }

    private fun levenshtein(s1: String, s2: String): Int {
        val dp = IntArray(s2.length + 1) { it }
        for (i in 1..s1.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..s2.length) {
                val temp = dp[j]
                dp[j] = if (s1[i - 1] == s2[j - 1]) prev else 1 + min(dp[j], min(dp[j - 1], prev))
                prev = temp
            }
        }
        return dp[s2.length]
    }
}
