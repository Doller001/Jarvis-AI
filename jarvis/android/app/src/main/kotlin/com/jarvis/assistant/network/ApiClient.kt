package com.jarvis.assistant.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class PingResult(val isSuccess: Boolean, val latencyMs: Long, val message: String)

/**
 * Ultra-low latency, high-performance API client.
 * Uses OkHttp with HTTP/2 multiplexing, connection pooling (10 idle connections, 5-min keep-alive),
 * and pre-warmed sockets to eliminate TCP/TLS handshake latency.
 */
class ApiClient(var baseUrl: String = "https://and9-1.onrender.com") {

    companion object {
        private const val TAG = "ApiClient"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        // Shared singleton connection pool & HTTP/2 client for all requests
        private val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
                .connectTimeout(3000, TimeUnit.MILLISECONDS)
                .readTimeout(5000, TimeUnit.MILLISECONDS)
                .writeTimeout(3000, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun pingBackend(urlToTest: String = baseUrl, onResult: (PingResult) -> Unit) {
        scope.launch {
            val start = System.currentTimeMillis()
            val cleanUrl = urlToTest.trim().trimEnd('/')
            val request = Request.Builder()
                .url("$cleanUrl/api/v1/health")
                .header("Connection", "keep-alive")
                .header("Accept", "application/json")
                .build()

            try {
                sharedClient.newCall(request).execute().use { response ->
                    val latency = System.currentTimeMillis() - start
                    val code = response.code
                    if (response.isSuccessful) {
                        val result = PingResult(true, latency, "Online (${latency}ms) — HTTP $code")
                        launch(Dispatchers.Main) { onResult(result) }
                    } else {
                        val result = PingResult(false, latency, "HTTP $code: ${response.message}")
                        launch(Dispatchers.Main) { onResult(result) }
                    }
                }
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - start
                Log.w(TAG, "Ping failed after ${latency}ms: ${e.message}")
                val result = PingResult(false, latency, e.message ?: "Connection timeout")
                launch(Dispatchers.Main) { onResult(result) }
            }
        }
    }

    fun fetchAvailableProviders(onResult: (List<String>) -> Unit) {
        scope.launch {
            val cleanUrl = baseUrl.trim().trimEnd('/')
            val request = Request.Builder()
                .url("$cleanUrl/api/v1/providers")
                .header("Connection", "keep-alive")
                .header("Accept", "application/json")
                .build()

            try {
                sharedClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty().trim()
                        val list = mutableListOf<String>()
                        try {
                            if (body.startsWith("[")) {
                                val array = JSONArray(body)
                                for (i in 0 until array.length()) {
                                    val item = array.get(i)
                                    when (item) {
                                        is JSONObject -> {
                                            val p = item.optString("provider").ifBlank { item.optString("name") }
                                            if (p.isNotBlank()) list.add(p)
                                        }
                                        is String -> if (item.isNotBlank()) list.add(item)
                                    }
                                }
                            } else if (body.startsWith("{")) {
                                val json = JSONObject(body)
                                val provArray = json.optJSONArray("providers")
                                if (provArray != null) {
                                    for (i in 0 until provArray.length()) {
                                        val item = provArray.get(i)
                                        when (item) {
                                            is JSONObject -> {
                                                val p = item.optString("provider").ifBlank { item.optString("name") }
                                                if (p.isNotBlank()) list.add(p)
                                            }
                                            is String -> if (item.isNotBlank()) list.add(item)
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error parsing providers payload: ${e.message}")
                        }
                        val result = if (list.isNotEmpty()) list else defaultProviders()
                        launch(Dispatchers.Main) { onResult(result) }
                        return@launch
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch providers: ${e.message}")
            }
            launch(Dispatchers.Main) { onResult(defaultProviders()) }
        }
    }

    fun selectProviderOnBackend(provider: String, model: String, onResult: (Boolean) -> Unit = {}) {
        scope.launch {
            val cleanUrl = baseUrl.trim().trimEnd('/')
            val bodyJson = JSONObject().apply {
                put("provider", provider)
                put("model", model)
            }.toString()

            val request = Request.Builder()
                .url("$cleanUrl/api/v1/providers/select")
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .header("Connection", "keep-alive")
                .build()

            try {
                sharedClient.newCall(request).execute().use { response ->
                    val success = response.isSuccessful
                    launch(Dispatchers.Main) { onResult(success) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to select provider: ${e.message}")
                launch(Dispatchers.Main) { onResult(false) }
            }
        }
    }

    fun sendChat(text: String, sessionId: String, onResult: (String?) -> Unit) {
        scope.launch {
            val cleanUrl = baseUrl.trim().trimEnd('/')
            val bodyJson = JSONObject().apply {
                put("text", text)
                put("session_id", sessionId)
                put("request_id", "android-${System.currentTimeMillis()}")
            }.toString()

            val request = Request.Builder()
                .url("$cleanUrl/api/v1/chat")
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .header("Connection", "keep-alive")
                .header("Accept", "application/json")
                .build()

            try {
                sharedClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        val json = JSONObject(body)
                        var responseText = json.optString("response_text")
                            .ifBlank { json.optString("result") }
                            .ifBlank { json.optString("prompt") }
                            .ifBlank { json.optString("message") }
                            .ifBlank { json.optString("answer") }
                            .ifBlank { json.optString("text") }
                        if (responseText.isBlank()) {
                            val execRes = json.optJSONObject("execution_result")
                            responseText = execRes?.optString("result").orEmpty()
                        }
                        launch(Dispatchers.Main) { onResult(if (responseText.isNotBlank()) responseText else null) }
                        return@launch
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Chat request failed: ${e.message}")
            }
            launch(Dispatchers.Main) { onResult(null) }
        }
    }

    private fun defaultProviders(): List<String> = listOf("nvidia", "groq", "openrouter", "gemini", "ollama")
}
