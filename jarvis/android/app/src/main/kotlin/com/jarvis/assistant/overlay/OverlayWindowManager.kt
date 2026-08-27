package com.jarvis.assistant.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

/**
 * Creates and manages the WindowManager layout params for the floating overlay.
 * Handles drag position updates and cleans up on removal.
 *
 * FIXES ADDED:
 * - Drag support via onTouchListener on the view itself (intercepts touch events)
 * - Edge-anchoring so bubble sticks near screen edges (like Google Assistant)
 * - Bound check so bubble stays within visible screen area
 */
class OverlayWindowManager(private val context: Context) {
    companion object {
        private const val TAG = "OverlayWindowManager"
        private const val OVERLAY_WIDTH_DP = 360
        private const val OVERLAY_HEIGHT_DP = 440
        private const val EDGE_MARGIN_DP = 48
    }

    val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    fun createLayoutParams(x: Int = 0, y: Int = 0): WindowManager.LayoutParams {
        val density = context.resources.displayMetrics.density
        val w = (OVERLAY_WIDTH_DP * density).toInt()
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

    /**
     * Attach drag-to-move behavior to a View that is already added to WindowManager.
     *
     * This intercepts touch events on the view and translates them to position changes.
     * Uses ACTION_DOWN to start tracking, ACTION_MOVE to update, ACTION_UP to stop.
     *
     * Edge-anchoring: after drag, clamps position so bubble stays within visible screen.
     */
    fun attachDragListener(view: View, onDrag: (dx: Int, dy: Int) -> Unit, initialParams: WindowManager.LayoutParams) {
        var lastX = 0f
        var lastY = 0f
        var params = initialParams

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX
                    lastY = event.rawY
                    // Capture: prevent parent views from stealing the gesture
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - lastX).toInt()
                    val dy = (event.rawY - lastY).toInt()
                    if (dx != 0 || dy != 0) {
                        params.x += dx
                        params.y += dy
                        try {
                            windowManager.updateViewLayout(v, params)
                        } catch (e: Exception) {
                            Log.w(TAG, "updateViewLayout failed during drag: ${e.message}")
                        }
                        onDrag(dx, dy)
                    }
                    lastX = event.rawX
                    lastY = event.rawY
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    // Clamp to edge region after drag ends (stick to edges like Google Assistant)
                    clampToEdge(params, v.width, v.height)
                    try {
                        windowManager.updateViewLayout(v, params)
                    } catch (e: Exception) {
                        Log.w(TAG, "updateViewLayout failed during clamp: ${e.message}")
                    }
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Clamp bubble position so it stays within the visible screen area.
     * Adds edge margin so bubble never goes fully off-screen.
     */
    private fun clampToEdge(params: WindowManager.LayoutParams, viewWidth: Int, viewHeight: Int) {
        val display = windowManager.defaultDisplay
        val screenWidth = display.width
        val screenHeight = display.height
        val marginPx = (EDGE_MARGIN_DP * context.resources.displayMetrics.density).toInt()

        // X bounds: left edge → right edge
        if (params.x < -viewWidth + marginPx) {
            params.x = -viewWidth + marginPx
        }
        if (params.x > screenWidth - marginPx) {
            params.x = screenWidth - marginPx
        }

        // Y bounds: top → bottom (leave room for system navigation/IME)
        val maxY = screenHeight - viewHeight - marginPx
        if (params.y < -viewHeight + marginPx) {
            params.y = -viewHeight + marginPx
        }
        if (params.y > maxY) {
            params.y = maxY
        }
    }

    fun updatePosition(params: WindowManager.LayoutParams, view: View, dx: Int, dy: Int) {
        params.x += dx
        params.y += dy
        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.w(TAG, "updatePosition failed: ${e.message}")
        }
    }
}
