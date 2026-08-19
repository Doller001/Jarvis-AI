package com.jarvis.assistant.accessibility

import android.util.Log

class GestureController {
    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300L) {
        Log.d("GestureController", "Dispatching gesture swipe from ($startX, $startY) to ($endX, $endY)")
    }
}
