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
import java.util.UUID
import java.util.concurrent.TimeUnit

data class PingResult(val isSuccess: Boolean, val latencyMs: Long, val message: String)
data class AuthTokens(val accessToken: String, val refreshToken: String, val expiresIn: Int, val deviceId: String, val trusted: Boolean)
data class ChatResult(
    val responseText: String? = null,
    val statusCode: Int? = null,
    val errorMessage: String? = null,
    val isNetworkFailure: Boolean = false
)

/**
 * Ultra-low latency, high-performance API client with automated JWT authentication & 401 recovery.
 */
class ApiClient(
    var baseUrl: String = "https://jarvis-ai-59qd.onrender.com",
    private val authTokenManager: AuthTokenManager? = null,
    private val authRepository: AuthRepository? = null
) {

    companion object {
        private const val TAG = "ApiClient"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
                .connectTimeout(3000, TimeUnit.MILLISECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(3000, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun addAuthHeaders(builder: Request.Builder) {
        builder.header("Connection", "keep-alive")
        builder.header("Accept", "application/json")
        authTokenManager?.accessToken?.let { token ->
            if (!authTokenManager.isTokenExpired(token)) {
                builder.header("Authorization", "Bearer $token")
            }
        }
    }

    fun pingBackend(urlToTest: String = baseUrl, onResult: (PingResult) -> Unit) {
        scope.launch {
            val start = System.currentTimeMillis()
            val cleanUrl = urlToTest.trim().trimEnd('/')
            val request = Request.Builder()
                .url("$cleanUrl/api/v1/health")
                .header("Connection", "keep-alive")
                .header("Accept", "application/json")
                .header("X-Request-ID", "ping-${UUID.randomUUID()}")
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

    fun registerDevice(
        deviceName: String,
        deviceModel: String,
        osVersion: String,
        onResult: (AuthTokens?) -> Unit
    ) {
        scope.launch {
            val cleanUrl = baseUrl.trim().trimEnd('/')
            val bodyJson = JSONObject().apply {
                put("device_name", deviceName)
                put("device_model", deviceModel)
                put("os_version", osVersion)
                authTokenManager?.deviceId?.let { put("device_id", it) }
            }.toString()

            val request = Request.Builder()
                .url("$cleanUrl/api/v1/auth/token")
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .header("Connection", "keep-alive")
                .header("Accept", "application/json")
                .header("X-Request-ID", "reg-${UUID.randomUUID()}")
                .build()

            try {
                sharedClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        val json = JSONObject(body)
                        val tokens = AuthTokens(
                            accessToken = json.getString("access_token"),
                            refreshToken = json.getString("refresh_token"),
                            expiresIn = json.getInt("expires_in"),
                            deviceId = json.getString("device_id"),
                            trusted = json.optBoolean("trusted", false)
                        )
                        authTokenManager?.saveTokens(
                            tokens.accessToken,
                            tokens.refreshToken,
                            tokens.deviceId,
                            tokens.trusted
                        )
                        launch(Dispatchers.Main) { onResult(tokens) }
                    } else {
                        Log.w(TAG, "Device registration failed: ${response.code}")
                        launch(Dispatchers.Main) { onResult(null) }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Device registration failed: ${e.message}")
                launch(Dispatchers.Main) { onResult(null) }
            }
        }
    }

    fun refreshAccessToken(onResult: (String?) -> Unit) {
        scope.launch {
            val refresh = authTokenManager?.refreshToken
            if (refresh == null) {
                launch(Dispatchers.Main) { onResult(null) }
                return@launch
            }

            val cleanUrl = baseUrl.trim().trimEnd('/')
            val bodyJson = JSONObject().apply {
                put("refresh_token", refresh)
            }.toString()

            val request = Request.Builder()
                .url("$cleanUrl/api/v1/auth/refresh")
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .header("Connection", "keep-alive")
                .header("X-Request-ID", "ref-${UUID.randomUUID()}")
                .build()

            try {
                sharedClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        val json = JSONObject(body)
                        val newAccess = json.getString("access_token")
                        val newRefresh = json.getString("refresh_token")
                        val deviceId = json.getString("device_id")
                        val trusted = json.optBoolean("trusted", false)
                        authTokenManager?.saveTokens(newAccess, newRefresh, deviceId, trusted)
                        launch(Dispatchers.Main) { onResult(newAccess) }
                    } else {
                        Log.w(TAG, "Token refresh failed: ${response.code}")
                        launch(Dispatchers.Main) { onResult(null) }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Token refresh failed: ${e.message}")
                launch(Dispatchers.Main) { onResult(null) }
            }
        }
    }

    fun fetchAvailableProviders(onResult: (List<String>) -> Unit) {
        scope.launch {
            val cleanUrl = baseUrl.trim().trimEnd('/')
            val request = Request.Builder()
                .url("$cleanUrl/api/v1/providers")
                .header("X-Request-ID", "prov-${UUID.randomUUID()}")
                .also { addAuthHeaders(it) }
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
                put("request_id", "sel-${UUID.randomUUID()}")
            }.toString()

            val request = Request.Builder()
                .url("$cleanUrl/api/v1/providers/select")
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .also { addAuthHeaders(it) }
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

    fun sendChat(text: String, sessionId: String, onResult: (ChatResult) -> Unit) {
        scope.launch {
            val cleanUrl = baseUrl.trim().trimEnd('/')
            val reqId = "req-chat-${UUID.randomUUID()}"
            val bodyJson = JSONObject().apply {
                put("text", text)
                put("session_id", sessionId)
                put("request_id", reqId)
            }.toString()

            val requestBuilder: (String) -> Request = { accessToken ->
                Request.Builder()
                    .url("$cleanUrl/api/v1/chat")
                    .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                    .header("Authorization", "Bearer $accessToken")
                    .header("Connection", "keep-alive")
                    .header("Accept", "application/json")
                    .header("X-Request-ID", reqId)
                    .build()
            }

            try {
                val response = if (authRepository != null) {
                    authRepository.executeAuthenticatedRequest(baseUrl, requestBuilder)
                } else {
                    val token = authTokenManager?.accessToken
                    if (token != null) {
                        sharedClient.newCall(requestBuilder(token)).execute()
                    } else null
                }

                if (response == null) {
                    launch(Dispatchers.Main) {
                        onResult(ChatResult(errorMessage = "Backend connection or authentication recovery failed.", isNetworkFailure = true))
                    }
                    return@launch
                }

                response.use { resp ->
                    val code = resp.code
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
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
                        launch(Dispatchers.Main) {
                            onResult(
                                ChatResult(
                                    responseText = responseText.ifBlank { null },
                                    statusCode = code,
                                    errorMessage = if (responseText.isBlank()) "Backend returned no answer." else null
                                )
                            )
                        }
                    } else {
                        val errorBody = resp.body?.string().orEmpty()
                        Log.w(TAG, "Chat request failed: HTTP $code $errorBody")
                        val errorMsg = try {
                            val errJson = JSONObject(errorBody).optJSONObject("error")
                            errJson?.optString("message") ?: "Backend returned HTTP $code"
                        } catch (_: Exception) {
                            "Backend returned HTTP $code"
                        }
                        launch(Dispatchers.Main) {
                            onResult(ChatResult(statusCode = code, errorMessage = errorMsg))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Chat request failed: ${e.message}")
                launch(Dispatchers.Main) {
                    onResult(ChatResult(errorMessage = e.message ?: "Network request failed.", isNetworkFailure = true))
                }
            }
        }
    }

    private fun defaultProviders(): List<String> = listOf("nvidia", "groq", "openrouter", "gemini", "ollama")
}

