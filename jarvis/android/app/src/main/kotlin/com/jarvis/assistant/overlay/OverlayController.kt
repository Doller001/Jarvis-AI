package com.jarvis.assistant.overlay

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.jarvis.assistant.services.JarvisOverlayService

/**
 * Single entry point for showing/hiding the floating overlay.
 *
 * Architecture:
 *   ViewModel / UI
 *       ↓
 *   OverlayController
 *       ↓
 *   JarvisOverlayService (WindowManager)
 */
object OverlayController {
    private const val TAG = "OverlayController"

    /** Check if the SYSTEM_ALERT_WINDOW permission is granted. */
    fun hasOverlayPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    /** Open system settings page so user can grant overlay permission. */
    fun openPermissionSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:${context.packageName}")
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
    }

    /** Show the floating overlay. Starts the service if not running. */
    fun show(context: Context) {
        if (!hasOverlayPermission(context)) {
            Log.w(TAG, "Cannot show overlay — SYSTEM_ALERT_WINDOW not granted")
            openPermissionSettings(context)
            return
        }
        val intent = Intent(context, JarvisOverlayService::class.java).apply {
            action = JarvisOverlayService.ACTION_SHOW
        }
        try {
            ContextCompat.startForegroundService(context, intent)
            Log.i(TAG, "Overlay SHOW requested")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start JarvisOverlayService", e)
        }
    }

    /** Hide the floating overlay without stopping the voice service. */
    fun hide(context: Context) {
        val intent = Intent(context, JarvisOverlayService::class.java).apply {
            action = JarvisOverlayService.ACTION_HIDE
        }
        try { context.startService(intent) } catch (_: Exception) {}
        Log.i(TAG, "Overlay HIDE requested")
    }

    /** Stop the overlay service entirely. */
    fun stop(context: Context) {
        val intent = Intent(context, JarvisOverlayService::class.java).apply {
            action = JarvisOverlayService.ACTION_STOP
        }
        try { context.startService(intent) } catch (_: Exception) {}
        Log.i(TAG, "Overlay STOP requested")
    }
}
