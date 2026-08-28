package com.jarvis.assistant.accessibility

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

class ScreenInspector {
    var enableLogging: Boolean = false

    fun inspectNodeTree(node: AccessibilityNodeInfo?) {
        if (!enableLogging || node == null) return
        val isPassword = node.isPassword
        val text = if (isPassword) "••••••••" else node.text?.toString() ?: ""
        if (text.isNotEmpty()) {
            Log.d("ScreenInspector", "Node class: ${node.className}, text: '$text'")
        }
        for (i in 0 until node.childCount) {
            inspectNodeTree(node.getChild(i))
        }
    }
}
