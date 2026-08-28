package com.jarvis.assistant.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.jarvis.assistant.permissions.PermissionManager
import com.jarvis.assistant.settings.SettingsManager

class BootRecoveryReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootRecoveryReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        if (context == null) return

        Log.i(TAG, "Device reboot detected. Initializing Jarvis recovery...")

        val settingsManager = SettingsManager(context)
        val permissionManager = PermissionManager(context)

        // Check if auto-start on boot is enabled
        val autoStartEnabled = settingsManager.autoStartOnBoot
        if (!autoStartEnabled) {
            Log.i(TAG, "Auto-start on boot is disabled. Skipping recovery.")
            return
        }

        // Check if accessibility service is enabled (required for full functionality)
        val permissionState = permissionManager.checkPermissionState()
        if (!permissionState.isAccessibilityGranted) {
            Log.w(TAG, "Accessibility service not enabled. Starting limited recovery mode.")
            startLimitedRecovery(context)
            return
        }

        // Full recovery: start foreground service
        Log.i(TAG, "Starting full Jarvis recovery...")
        startFullRecovery(context)
    }

    private fun startFullRecovery(context: Context) {
        try {
            val serviceIntent = Intent(context, JarvisForegroundService::class.java).apply {
                action = JarvisForegroundService.ACTION_START
            }
            ContextCompat.startForegroundService(context, serviceIntent)
            Log.i(TAG, "Foreground service started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service: ${e.message}", e)
        }
    }

    private fun startLimitedRecovery(context: Context) {
        // In limited mode, we can't use accessibility features
        // Just ensure the app is ready for user interaction
        Log.i(TAG, "Limited recovery: Accessibility service required for full functionality")
        // The user will need to manually enable accessibility and then start the service
    }
}
