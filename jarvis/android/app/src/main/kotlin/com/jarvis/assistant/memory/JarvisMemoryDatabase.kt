package com.jarvis.assistant.memory

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * SQLite Database OpenHelper for the JARVIS Memory Engine.
 * Stored at app-private external storage: /files/memory/db.sqlite3
 * Features:
 * - WAL journal mode
 * - CAG (Cache Augmented Generation) table & FTS5
 * - RAG (Retrieval Augmented Generation) table & FTS5
 * - MAG (Memory Augmented Generation) facts, user profile & episodes
 * - Offline Pending Query Queue
 */
class JarvisMemoryDatabase(context: Context) : SQLiteOpenHelper(
    context,
    getDatabasePath(context),
    null,
    DATABASE_VERSION
) {
    companion object {
        private const val TAG = "JarvisMemoryDb"
        private const val DATABASE_NAME = "db.sqlite3"
        private const val DATABASE_VERSION = 1

        fun getDatabasePath(context: Context): String {
            val memoryDir = context.getExternalFilesDir("memory") ?: File(context.filesDir, "memory")
            if (!memoryDir.exists()) {
                memoryDir.mkdirs()
            }
            return File(memoryDir, DATABASE_NAME).absolutePath
        }

        fun sha256(input: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }

    private val memoryFolder: File = context.getExternalFilesDir("memory") ?: File(context.filesDir, "memory")

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        Log.i(TAG, "Creating JARVIS Memory Engine SQLite schema...")

        // 1. CAG Cache (Cache Augmented Generation)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS cag_cache (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                q_norm TEXT NOT NULL,
                q_hash TEXT NOT NULL UNIQUE,
                answer TEXT NOT NULL,
                emb BLOB,
                hits INTEGER NOT NULL DEFAULT 1,
                last_used INTEGER NOT NULL,
                created INTEGER NOT NULL
            );
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_cag_hash ON cag_cache(q_hash);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_cag_used ON cag_cache(last_used);")

        // 2. RAG Chunks (Retrieval Augmented Generation)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS rag_chunks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                doc_id TEXT NOT NULL,
                chunk_idx INTEGER NOT NULL,
                text TEXT NOT NULL,
                emb BLOB,
                src TEXT,
                ts INTEGER NOT NULL
            );
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_rag_doc ON rag_chunks(doc_id);")

        // 3. MAG Facts (Memory Augmented Generation - Long-term)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS mem_facts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                type TEXT NOT NULL,
                text TEXT NOT NULL,
                emb BLOB,
                confidence REAL NOT NULL DEFAULT 0.8,
                ts INTEGER NOT NULL,
                last_used INTEGER NOT NULL
            );
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_mem_type ON mem_facts(type);")

        // 4. MAG Episodes (Conversation History)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS mem_episodes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ts INTEGER NOT NULL,
                role TEXT NOT NULL,
                text TEXT NOT NULL
            );
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ep_ts ON mem_episodes(ts);")

        // 5. MAG User Profile (Key-Value Key Facts)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS mem_user (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                key TEXT NOT NULL UNIQUE,
                value TEXT NOT NULL,
                ts INTEGER NOT NULL
            );
        """.trimIndent())

        // 6. Pending Offline Query Queue
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS pending_q (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                query TEXT NOT NULL,
                emb BLOB,
                asked_ts INTEGER NOT NULL,
                resolved INTEGER NOT NULL DEFAULT 0
            );
        """.trimIndent())

        seedInitialMemory(db)
        writeMetaJson()
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.w(TAG, "Upgrading memory database from $oldVersion to $newVersion")
    }

    private fun seedInitialMemory(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()

        // Seed Core User & Assistant Profile
        val userValues = listOf(
            Triple("owner_name", "Minaty", now),
            Triple("assistant_name", "JARVIS", now),
            Triple("assistant_class", "AGI Cognitive Assistant", now),
            Triple("tagline", "I anticipate, I protect, I execute. You think; I handle the rest.", now)
        )
        for ((k, v, ts) in userValues) {
            db.execSQL("INSERT OR REPLACE INTO mem_user(key, value, ts) VALUES (?, ?, ?);", arrayOf(k, v, ts))
        }

        // Seed Pre-trained CAG Cache (Sub-ms Instant Responses)
        val cagSeeds = listOf(
            "who are you" to "I am JARVIS, an AGI-class cognitive assistant created by Minaty. I anticipate, I protect, I execute.",
            "what is your name" to "My name is JARVIS. Created by Minaty as your trusted personal cognitive operator.",
            "who made you" to "I was created by Minaty as an AGI-class personal cognitive assistant.",
            "who created you" to "Minaty created me to assist with reasoning, automation, and device control.",
            "hello" to "JARVIS online. Good to see you, Minaty. What shall we build today?",
            "hi" to "Hello Minaty. JARVIS online and standing by.",
            "how are you" to "All systems operating at peak efficiency, Minaty. Ready for your command.",
            "what can you do" to "I can control device hardware (Torch, Wi-Fi, Bluetooth), launch or close apps, check battery & storage, read screen, manage WhatsApp & calls, and reason across complex workflows.",
            "thank you" to "Always at your service, Minaty.",
            "thanks" to "Always a pleasure, Minaty.",
            "bye" to "Goodbye, Minaty. Standing by in low-power background monitoring."
        )

        for ((q, ans) in cagSeeds) {
            val qNorm = q.trim().lowercase()
            val qHash = sha256(qNorm)
            val cv = ContentValues().apply {
                put("q_norm", qNorm)
                put("q_hash", qHash)
                put("answer", ans)
                put("hits", 5)
                put("last_used", now)
                put("created", now)
            }
            db.insertWithOnConflict("cag_cache", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }

        // Seed RAG Knowledge Chunks
        val ragSeeds = listOf(
            Triple("doc-core", 0, "JARVIS is an AGI-class cognitive assistant designed for Minaty with local-first offline execution, low latency, and hardware automation capabilities."),
            Triple("doc-voice", 1, "Voice architecture includes 2nd-order Butterworth high-pass filtering (85Hz/135Hz), adaptive noise floor tracking, nearest-voice proximity gating, and automatic Bluetooth microphone routing."),
            Triple("doc-storage", 2, "Memory Engine consists of CAG (Cache Augmented Generation), RAG (Retrieval Augmented Generation), and MAG (Memory Augmented Generation) with local SQLite3 persistence.")
        )
        for ((docId, chunkIdx, txt) in ragSeeds) {
            val cv = ContentValues().apply {
                put("doc_id", docId)
                put("chunk_idx", chunkIdx)
                put("text", txt)
                put("src", "system_seed")
                put("ts", now)
            }
            db.insert("rag_chunks", null, cv)
        }

        // Seed MAG Facts
        val memFacts = listOf(
            Pair("user_pref", "User is Minaty; preferences prioritize fast local execution and high-accuracy voice recognition."),
            Pair("semantic", "JARVIS operates with composed British polish, dry wit, and decisive execution without fluff.")
        )
        for ((type, txt) in memFacts) {
            val cv = ContentValues().apply {
                put("type", type)
                put("text", txt)
                put("confidence", 1.0)
                put("ts", now)
                put("last_used", now)
            }
            db.insert("mem_facts", null, cv)
        }

        Log.i(TAG, "Seeded initial CAG, RAG, and MAG memory facts successfully.")
    }

    fun writeMetaJson() {
        try {
            val metaFile = File(memoryFolder, "meta.json")
            val json = JSONObject().apply {
                put("schema_version", DATABASE_VERSION)
                put("app_version", "1.0.0")
                put("created_epoch", System.currentTimeMillis())
                put("engine", "CAG+RAG+MAG")
                put("last_write", System.currentTimeMillis())
            }
            metaFile.writeText(json.toString(2))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write meta.json", e)
        }
    }
}
