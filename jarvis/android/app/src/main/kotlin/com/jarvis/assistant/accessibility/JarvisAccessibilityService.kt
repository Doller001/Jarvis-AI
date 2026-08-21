package com.jarvis.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class JarvisAccessibilityService : AccessibilityService() {
    private val screenInspector = ScreenInspector()
    private val controller = AccessibilityController(this)

    companion object {
        var instance: JarvisAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i("JarvisAccessibility", "Accessibility service connected.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val rootNode = rootInActiveWindow ?: return
        screenInspector.inspectNodeTree(rootNode)
    }

    override fun onInterrupt() {
        Log.w("JarvisAccessibility", "Accessibility service interrupted.")
    }

    override fun onDestroy() {
        if (instance == this) instance = null
        super.onDestroy()
    }
}
