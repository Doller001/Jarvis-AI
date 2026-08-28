package com.jarvis.assistant.device

import android.content.Context
import android.content.Intent
import android.util.Log
import com.jarvis.assistant.accessibility.AccessibilityController

class DisplayController(private val context: Context? = null) {

    private val accessibilityController = AccessibilityController()

    /**
     * Capture a screenshot.
     * Primary: Uses AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT (API 28+, Android 9+).
     * Fallback: System screenshot broadcast or opening settings.
     */
    fun takeScreenshot(): Boolean {
        Log.i("DisplayController", "Attempting screenshot")
        if (accessibilityController.takeScreenshot()) {
            return true
        }

        val ctx = context ?: return false
        return try {
            val intent = Intent("com.android.systemui.screenshot").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (intent.resolveActivity(ctx.packageManager) != null) {
                ctx.sendBroadcast(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("DisplayController", "Screenshot failed", e)
            false
        }
    }
}
