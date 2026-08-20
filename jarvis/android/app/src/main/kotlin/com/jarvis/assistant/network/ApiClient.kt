package com.jarvis.assistant.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class ApiClient(val baseUrl: String = "https://and9-1.onrender.com") {

    fun fetchAvailableProviders(onResult: (List<String>) -> Unit) {
        Log.i("ApiClient", "Fetching active providers from $baseUrl/api/v1/providers")
        GlobalScope.launch(Dispatchers.IO) {
            val providers = try {
                val url = URL("$baseUrl/api/v1/providers")
                val conn = url.openConnection() as HttpURLConnection
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
            }
            launch(Dispatchers.Main) {
                onResult(providers)
            }
        }
    }

    fun selectProviderOnBackend(provider: String, model: String, onResult: (Boolean) -> Unit = {}) {
        GlobalScope.launch(Dispatchers.IO) {
            val success = try {
                val url = URL("$baseUrl/api/v1/providers/select")
                val conn = url.openConnection() as HttpURLConnection
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
            }
            launch(Dispatchers.Main) {
                onResult(success)
            }
        }
    }

    private fun defaultProviders(): List<String> = listOf("nvidia", "groq", "openrouter", "gemini", "ollama")
}
