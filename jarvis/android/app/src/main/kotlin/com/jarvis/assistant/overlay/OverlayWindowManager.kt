package com.jarvis.assistant.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.WindowManager

/**
 * Creates and manages the WindowManager layout params for the floating overlay.
 * Handles drag position updates and cleans up on removal.
 */
class OverlayWindowManager(private val context: Context) {
    companion object {
        private const val TAG = "OverlayWindowManager"
        private const val OVERLAY_WIDTH_DP  = 360
        private const val OVERLAY_HEIGHT_DP = 440
    }

    val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    fun createLayoutParams(x: Int = 0, y: Int = 0): WindowManager.LayoutParams {
        val density = context.resources.displayMetrics.density
        val w = (OVERLAY_WIDTH_DP  * density).toInt()
        val h = (OVERLAY_HEIGHT_DP * density).toInt()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            w, h, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
    }

    fun updatePosition(params: WindowManager.LayoutParams, view: android.view.View, dx: Int, dy: Int) {
        params.x += dx
        params.y += dy
        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.w(TAG, "updatePosition failed: ${e.message}")
        }
    }
}
