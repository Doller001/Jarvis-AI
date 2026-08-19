package com.jarvis.assistant.accessibility

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

class ScreenInspector {
    fun inspectNodeTree(node: AccessibilityNodeInfo?) {
        if (node == null) return
        val isPassword = node.isPassword
        val text = if (isPassword) "••••••••" else node.text?.toString() ?: ""
        if (text.isNotEmpty()) {
            Log.v("ScreenInspector", "Node class: ${node.className}, text: '$text' (masked: $isPassword)")
        }
        for (i in 0 until node.childCount) {
            inspectNodeTree(node.getChild(i))
        }
    }
}
