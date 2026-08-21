package com.jarvis.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log

class GestureController(private val service: AccessibilityService? = null) {

    private val activeService: AccessibilityService?
        get() = service ?: JarvisAccessibilityService.instance

    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300L): Boolean {
        val s = activeService ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            Log.d("GestureController", "Dispatching gesture swipe from ($startX, $startY) to ($endX, $endY)")
            return s.dispatchGesture(gesture, null, null)
        }
        return false
    }

    fun tap(x: Float, y: Float, durationMs: Long = 50L): Boolean {
        val s = activeService ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply {
                moveTo(x, y)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            Log.d("GestureController", "Dispatching gesture tap at ($x, $y)")
            return s.dispatchGesture(gesture, null, null)
        }
        return false
    }
}
