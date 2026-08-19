package com.jarvis.assistant.accessibility

import android.view.accessibility.AccessibilityNodeInfo

class NodeFinder {
    fun findNodeByText(root: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (root == null) return null
        val nodes = root.findAccessibilityNodeInfosByText(text)
        return nodes?.firstOrNull()
    }
}
