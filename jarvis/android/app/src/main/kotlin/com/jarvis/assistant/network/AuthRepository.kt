package com.jarvis.assistant.network

import android.content.Context
import android.os.Build
import android.util.Log
import com.jarvis.assistant.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID

enum class DiagnosticState {
    ONLINE,
    DEGRADED,
    BACKEND_STARTING,
    BACKEND_TIMEOUT,
    AUTH_EXPIRED,
    AUTH_INVALID,
    DEVICE_UNKNOWN,
    PROVIDER_UNAVAILABLE,
    NETWORK_OFFLINE
}

data class AuthState(
    val isAuthenticated: Boolean,
    val deviceId: String?,
    val diagnosticState: DiagnosticState = DiagnosticState.ONLINE,
    val diagnosticMessage: String = ""
)

/**
 * Canonical Authentication & Token Lifecycle Repository for JARVIS.
 * Enforces:
 * 1. Proactive token refresh (< 2 minutes remaining)
 * 2. Deterministic 401 Recovery: Request -> 401 -> Refresh -> Retry (1x) -> If Refresh fails -> Re-Register -> Retry (1x)
 * 3. Mutex synchronization preventing redundant concurrent token requests
 */
class AuthRepository(
    private val context: Context,
    private val authTokenManager: AuthTokenManager = AuthTokenManager(context),
    private val settingsManager: SettingsManager = SettingsManager(context),
    private val httpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "AuthRepository"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val PROACTIVE_REFRESH_WINDOW_MS = 120_000L // 2 minutes
    }

    private val authMutex = Mutex()

    val deviceId: String? get() = authTokenManager.deviceId
    val isTrusted: Boolean get() = authTokenManager.isTrusted
    val isAuthenticated: Boolean get() = authTokenManager.isAuthenticated

    /**
     * Obtains a valid, active access token. Proactively refreshes if expiring in <= 2 mins.
     */
    suspend fun getValidAccessToken(baseUrl: String): String? = authMutex.withLock {
        withContext(Dispatchers.IO) {
            val token = authTokenManager.accessToken
            if (token != null) {
                val expiryMs = authTokenManager.getTokenExpiryMs(token)
                if (expiryMs > PROACTIVE_REFRESH_WINDOW_MS) {
                    return@withContext token
                }
                Log.d(TAG, "Access token expiring in ${expiryMs / 1000}s — performing proactive refresh")
            }

            // Attempt token refresh
            val refreshed = refreshAccessTokenInternal(baseUrl)
            if (refreshed != null) {
                return@withContext refreshed
            }

            // Refresh failed or no refresh token -> re-register device
            Log.i(TAG, "Token refresh unavailable — registering device identity")
            val registered = registerDeviceInternal(baseUrl)
            return@withContext registered?.accessToken
        }
    }

    /**
     * Executes an authenticated HTTP request with deterministic 401 retry interceptor.
     * Loop: Request -> 401? -> Refresh -> Retry 1x -> Re-register -> Retry 1x -> Fail.
     */
    suspend fun executeAuthenticatedRequest(
        baseUrl: String,
        builderFactory: (accessToken: String) -> Request
    ): okhttp3.Response? = withContext(Dispatchers.IO) {
        val initialToken = getValidAccessToken(baseUrl)
            ?: return@withContext null

        val req1 = builderFactory(initialToken)
        val resp1 = try {
            httpClient.newCall(req1).execute()
        } catch (e: Exception) {
            Log.w(TAG, "HTTP execution failure: ${e.message}")
            return@withContext null
        }

        if (resp1.code != 401) {
            return@withContext resp1
        }
        resp1.close()

        Log.w(TAG, "HTTP 401 received — initiating single refresh recovery")
        val refreshedToken = authMutex.withLock {
            refreshAccessTokenInternal(baseUrl)
        }

        if (refreshedToken != null) {
            val req2 = builderFactory(refreshedToken)
            val resp2 = try {
                httpClient.newCall(req2).execute()
            } catch (e: Exception) {
                Log.w(TAG, "HTTP retry execution failure: ${e.message}")
                return@withContext null
            }
            if (resp2.code != 401) {
                return@withContext resp2
            }
            resp2.close()
        }

        Log.w(TAG, "Refresh retry failed with 401 — initiating single re-registration fallback")
        val reRegisteredTokens = authMutex.withLock {
            registerDeviceInternal(baseUrl)
        }

        if (reRegisteredTokens != null) {
            val req3 = builderFactory(reRegisteredTokens.accessToken)
            return@withContext try {
                httpClient.newCall(req3).execute()
            } catch (e: Exception) {
                Log.w(TAG, "HTTP re-registration retry failure: ${e.message}")
                null
            }
        }

        Log.e(TAG, "Auth recovery exhausted — returning null")
        null
    }

    private fun refreshAccessTokenInternal(baseUrl: String): String? {
        val refresh = authTokenManager.refreshToken ?: return null
        val cleanUrl = baseUrl.trim().trimEnd('/')
        val bodyJson = JSONObject().apply {
            put("refresh_token", refresh)
        }.toString()

        val req = Request.Builder()
            .url("$cleanUrl/api/v1/auth/refresh")
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .header("X-Request-ID", "req-ref-${UUID.randomUUID()}")
            .build()

        return try {
            httpClient.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val json = JSONObject(body)
                    val newAccess = json.getString("access_token")
                    val newRefresh = json.getString("refresh_token")
                    val devId = json.getString("device_id")
                    val trusted = json.optBoolean("trusted", false)
                    authTokenManager.saveTokens(newAccess, newRefresh, devId, trusted)
                    Log.i(TAG, "Access token refreshed and rotated successfully")
                    newAccess
                } else {
                    Log.w(TAG, "Token refresh rejected with HTTP ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Token refresh network exception: ${e.message}")
            null
        }
    }

    private fun registerDeviceInternal(baseUrl: String): AuthTokens? {
        val cleanUrl = baseUrl.trim().trimEnd('/')
        val deviceName = settingsManager.deviceId ?: Build.MODEL ?: "android-device"
        val deviceModel = Build.MODEL ?: "unknown"
        val osVersion = "Android ${Build.VERSION.RELEASE}"

        val bodyJson = JSONObject().apply {
            put("device_name", deviceName)
            put("device_model", deviceModel)
            put("os_version", osVersion)
            authTokenManager.deviceId?.let { put("device_id", it) }
        }.toString()

        val req = Request.Builder()
            .url("$cleanUrl/api/v1/auth/token")
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .header("X-Request-ID", "req-reg-${UUID.randomUUID()}")
            .build()

        return try {
            httpClient.newCall(req).execute().use { response ->
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
                    authTokenManager.saveTokens(
                        tokens.accessToken,
                        tokens.refreshToken,
                        tokens.deviceId,
                        tokens.trusted
                    )
                    Log.i(TAG, "Device registered and new token pair saved (${tokens.deviceId})")
                    tokens
                } else {
                    Log.e(TAG, "Device registration failed with HTTP ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Device registration network exception: ${e.message}")
            null
        }
    }
}
