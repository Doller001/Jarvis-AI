package com.jarvis.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class JarvisAccessibilityService : AccessibilityService() {
    private val screenInspector = ScreenInspector()
    private val controller = AccessibilityController(this)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val rootNode = rootInActiveWindow ?: return
        screenInspector.inspectNodeTree(rootNode)
    }

    override fun onInterrupt() {
        Log.w("JarvisAccessibility", "Accessibility service interrupted.")
    }
}
