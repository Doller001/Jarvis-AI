package com.jarvis.assistant.permissions

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import android.util.Log

/**
 * Real permission state for the Jarvis runtime.
 * All checks query the actual system — no stubbed "true" returns.
 */
data class PermissionState(
    val isMicrophoneGranted: Boolean = false,
    val isNotificationGranted: Boolean = false,
    val isAccessibilityGranted: Boolean = false,
    val isBatteryOptimizationIgnored: Boolean = false,
    val isCameraGranted: Boolean = false,
    val isCallPhoneGranted: Boolean = false,
    val isContactsGranted: Boolean = false,
    val isSmsGranted: Boolean = false,
    val isDigitalAssistant: Boolean = false
) {
    /** Required permissions must be granted before the assistant goes live. */
    val allRequiredGranted: Boolean
        get() = isMicrophoneGranted &&
                isNotificationGranted &&
                isAccessibilityGranted &&
                isBatteryOptimizationIgnored &&
                isCallPhoneGranted &&
                isContactsGranted &&
                isSmsGranted &&
                isDigitalAssistant

    val grantedCount: Int
        get() = listOf(
            isMicrophoneGranted, isNotificationGranted, isAccessibilityGranted,
            isBatteryOptimizationIgnored, isCameraGranted, isCallPhoneGranted,
            isContactsGranted, isSmsGranted, isDigitalAssistant
        ).count { it }
}

class PermissionManager(private val context: Context? = null) {

    private fun Context.has(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    fun checkPermissionState(): PermissionState {
        val ctx = context ?: return PermissionState()
        Log.i("PermissionManager", "Checking Jarvis runtime permissions...")

        val isNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ctx.has(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }

        val isBattery = runCatching {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(ctx.packageName)
        }.getOrDefault(false)

        val isAcc = isAccessibilityServiceEnabled(ctx)
        val isAssistant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val rm = ctx.getSystemService(android.app.role.RoleManager::class.java)
                rm.isRoleHeld(android.app.role.RoleManager.ROLE_ASSISTANT)
            }.getOrDefault(false)
        } else {
            false
        }

        return PermissionState(
            isMicrophoneGranted = ctx.has(android.Manifest.permission.RECORD_AUDIO),
            isNotificationGranted = isNotif,
            isAccessibilityGranted = isAcc,
            isBatteryOptimizationIgnored = isBattery,
            isCameraGranted = ctx.has(android.Manifest.permission.CAMERA),
            isCallPhoneGranted = ctx.has(android.Manifest.permission.CALL_PHONE),
            isContactsGranted = ctx.has(android.Manifest.permission.READ_CONTACTS),
            isSmsGranted = ctx.has(android.Manifest.permission.SEND_SMS),
            isDigitalAssistant = isAssistant
        )
    }

    /**
     * Detects whether an accessibility service matching this app is enabled in
     * Settings > Accessibility. Uses the canonical secure-settings flat string.
     */
    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = "${context.packageName}/com.jarvis.assistant.accessibility.JarvisAccessibilityService"
        val enabledServices = runCatching {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
        }.getOrDefault("")
        return enabledServices.split(":").any { it.equals(expected, ignoreCase = true) }
    }
}

/** Convenience accessor used by the UI layer. */
class AccessibilityManager(private val context: Context? = null) {
    private val pm = context?.let { PermissionManager(it) }
    fun isAccessibilityServiceEnabled(): Boolean = pm?.checkPermissionState()?.isAccessibilityGranted ?: false
}
