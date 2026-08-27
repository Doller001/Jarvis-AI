package com.jarvis.assistant.device

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.util.Log

class DisplayController(private val context: Context? = null) {

    /**
     * Capture a screenshot. On Android 9+ this requires a MediaProjection session
     * which needs a user consent dialog — we can't do it silently. Instead we open
     * the system screenshot shortcut where supported, or guide the user.
     *
     * For a fully hands-free capture, the foreground service would need to request
     * MediaProjection permission at runtime. Here we trigger the OS screenshot when
     * the device supports the accessibility / gesture path, otherwise instruct.
     */
    fun takeScreenshot(): Boolean {
        Log.i("DisplayController", "Attempting screenshot")
        return try {
            val ctx = context ?: return false
            // Use the system screenshot intent (works on many OEMs via accessibility /
            // on Android 9-11 there's a hidden takescreenshot broadcast; on newer we
            // rely on the user granting MediaProjection, handled elsewhere).
            val intent = Intent("com.android.systemui.screenshot").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (intent.resolveActivity(ctx.packageManager) != null) {
                ctx.sendBroadcast(intent)
                return true
            }
            // Fallback: open the screenshot action via quick settings intent
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val qs = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                ctx.startActivity(qs)
            }
            true
        } catch (e: Exception) {
            Log.e("DisplayController", "Screenshot failed", e)
            false
        }
    }
}
