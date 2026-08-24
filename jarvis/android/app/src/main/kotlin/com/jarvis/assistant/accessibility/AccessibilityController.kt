package com.jarvis.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

class AccessibilityController(private val service: AccessibilityService? = null) {

    private val activeService: AccessibilityService?
        get() = service ?: JarvisAccessibilityService.instance

    fun tap(targetText: String): Boolean {
        Log.i("AccessibilityController", "Tapping node with text: '$targetText'")
        val root = activeService?.rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(targetText)
        for (node in nodes) {
            if (performClick(node)) return true
        }
        val fallbackNode = findClickableByTextOrDesc(root, targetText)
        if (fallbackNode != null && performClick(fallbackNode)) {
            return true
        }
        return false
    }

    private fun findClickableByTextOrDesc(node: AccessibilityNodeInfo?, target: String): AccessibilityNodeInfo? {
        if (node == null) return null
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        if (text.contains(target, ignoreCase = true) || desc.contains(target, ignoreCase = true)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findClickableByTextOrDesc(child, target)
            if (found != null) return found
        }
        return null
    }

    fun tapById(viewId: String): Boolean {
        Log.i("AccessibilityController", "Tapping node by ID: '$viewId'")
        val root = activeService?.rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        for (node in nodes) {
            if (performClick(node)) return true
        }
        return false
    }

    /** Best-effort click used after YouTube search results have loaded. */
    fun tapFirstVideoResult(): Boolean {
        val root = activeService?.rootInActiveWindow ?: return false
        val candidate = findFirstVideoCandidate(root)
        return candidate != null && performClick(candidate)
    }

    fun tapAny(labels: List<String>): Boolean {
        return labels.any { label -> tap(label) }
    }

    private fun findFirstVideoCandidate(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null || !node.isVisibleToUser) return null
        val value = (node.text?.toString() ?: node.contentDescription?.toString()).orEmpty().trim()
        val ignored = setOf("home", "shorts", "subscriptions", "library", "search", "create")
        if (node.isClickable && value.length >= 8 && value.lowercase() !in ignored) return node
        for (i in 0 until node.childCount) {
            val found = findFirstVideoCandidate(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    private fun performClick(node: AccessibilityNodeInfo?): Boolean {
        var current = node
        while (current != null) {
            if (current.isClickable) {
                val ok = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (ok) return true
            }
            current = current.parent
        }
        return false
    }

    fun scroll(direction: String = "down"): Boolean {
        Log.i("AccessibilityController", "Scrolling screen $direction")
        val root = activeService?.rootInActiveWindow ?: return false
        val action = if (direction.equals("up", ignoreCase = true)) {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }
        return root.performAction(action)
    }

    fun back(): Boolean {
        Log.i("AccessibilityController", "Performing GLOBAL_ACTION_BACK")
        return activeService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) ?: false
    }

    fun home(): Boolean {
        Log.i("AccessibilityController", "Performing GLOBAL_ACTION_HOME")
        return activeService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) ?: false
    }

    fun openRecents(): Boolean {
        Log.i("AccessibilityController", "Performing GLOBAL_ACTION_RECENTS")
        return activeService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) ?: false
    }

    fun typeText(text: String): Boolean {
        Log.i("AccessibilityController", "Typing text into active field: '$text'")
        val root = activeService?.rootInActiveWindow ?: return false
        val focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: findFocusedOrEditableNode(root)
            ?: return false
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    private fun findFocusedOrEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isFocused || node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFocusedOrEditableNode(child)
            if (found != null) return found
        }
        return null
    }

    fun readScreen(): String {
        Log.i("AccessibilityController", "Reading interactive screen elements")
        val root = activeService?.rootInActiveWindow ?: return "Accessibility Service not bound"
        val sb = StringBuilder()
        collectScreenText(root, sb)
        return if (sb.isNotBlank()) sb.toString() else "Screen contains no text elements"
    }

    private fun collectScreenText(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (node == null || !node.isVisibleToUser) return
        if (node.isPassword) {
            sb.append("[Password field] ")
            return
        }
        val text = node.text?.toString() ?: node.contentDescription?.toString()
        if (!text.isNullOrBlank()) {
            sb.append(text.trim()).append(" | ")
        }
        for (i in 0 until node.childCount) {
            collectScreenText(node.getChild(i), sb)
        }
    }
}
