package com.jarvis.assistant.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class PingResult(val isSuccess: Boolean, val latencyMs: Long, val message: String)

class ApiClient(var baseUrl: String = "https://and9-1.onrender.com") {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun pingBackend(urlToTest: String = baseUrl, onResult: (PingResult) -> Unit) {
        scope.launch {
            val start = System.currentTimeMillis()
            var conn: HttpURLConnection? = null
            val result = try {
                val cleanUrl = urlToTest.trim().trimEnd('/')
                val url = URL("$cleanUrl/api/v1/health")
                conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 4000
                conn.readTimeout = 4000

                val code = conn.responseCode
                val latency = System.currentTimeMillis() - start
                if (code in 200..299) {
                    PingResult(true, latency, "Online (${latency}ms) — HTTP $code")
                } else {
                    PingResult(false, latency, "HTTP error $code")
                }
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - start
                PingResult(false, latency, e.message ?: "Connection failed")
            } finally {
                conn?.disconnect()
            }
            launch(Dispatchers.Main) {
                onResult(result)
            }
        }
    }

    fun fetchAvailableProviders(onResult: (List<String>) -> Unit) {
        Log.i("ApiClient", "Fetching active providers from $baseUrl/api/v1/providers")
        scope.launch {
            var conn: HttpURLConnection? = null
            val providers = try {
                val url = URL("$baseUrl/api/v1/providers")
                conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = reader.readText()
                    reader.close()

                    val array = JSONArray(response)
                    val list = mutableListOf<String>()
                    for (i in 0 until array.length()) {
                        list.add(array.getJSONObject(i).getString("provider"))
                    }
                    if (list.isNotEmpty()) list else defaultProviders()
                } else {
                    defaultProviders()
                }
            } catch (e: Exception) {
                Log.w("ApiClient", "Failed to fetch providers via HTTP: ${e.message}. Using default list.")
                defaultProviders()
            } finally {
                conn?.disconnect()
            }
            launch(Dispatchers.Main) {
                onResult(providers)
            }
        }
    }

    fun selectProviderOnBackend(provider: String, model: String, onResult: (Boolean) -> Unit = {}) {
        scope.launch {
            var conn: HttpURLConnection? = null
            val success = try {
                val url = URL("$baseUrl/api/v1/providers/select")
                conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 5000

                val body = JSONObject().apply {
                    put("provider", provider)
                    put("model", model)
                }
                conn.outputStream.use { os ->
                    os.write(body.toString().toByteArray())
                }

                conn.responseCode in 200..299
            } catch (e: Exception) {
                Log.w("ApiClient", "Failed to select provider on backend: ${e.message}")
                false
            } finally {
                conn?.disconnect()
            }
            launch(Dispatchers.Main) {
                onResult(success)
            }
        }
    }

    fun sendChat(text: String, sessionId: String, onResult: (String?) -> Unit) {
        scope.launch {
            var conn: HttpURLConnection? = null
            val responseText = try {
                val url = URL("$baseUrl/api/v1/chat")
                conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 10000
                conn.readTimeout = 60000
                conn.doOutput = true

                val body = JSONObject().apply {
                    put("text", text)
                    put("session_id", sessionId)
                    put("request_id", "android-${System.currentTimeMillis()}")
                }
                conn.outputStream.use { os -> os.write(body.toString().toByteArray()) }

                if (conn.responseCode in 200..299) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val json = JSONObject(reader.readText())
                    reader.close()
                    json.optString("response_text").ifBlank { json.optString("prompt") }.ifBlank { json.optString("message") }
                } else null
            } catch (e: Exception) {
                Log.w("ApiClient", "Chat request failed: ${e.message}")
                null
            } finally {
                conn?.disconnect()
            }
            launch(Dispatchers.Main) { onResult(responseText) }
        }
    }

    private fun defaultProviders(): List<String> = listOf("nvidia", "groq", "openrouter", "gemini", "ollama")
}
