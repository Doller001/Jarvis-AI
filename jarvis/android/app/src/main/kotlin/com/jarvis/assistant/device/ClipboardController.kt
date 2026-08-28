package com.jarvis.assistant.device

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log

class ClipboardController(private val context: Context? = null) {

    fun copyToClipboard(text: String): Boolean {
        Log.i("ClipboardController", "Copying to clipboard: '$text'")
        return try {
            val ctx = context ?: return false
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
            val clip = ClipData.newPlainText("JARVIS", text)
            cm.setPrimaryClip(clip)
            true
        } catch (e: Exception) {
            Log.e("ClipboardController", "Failed to copy", e)
            false
        }
    }

    fun readClipboard(): String {
        return try {
            val ctx = context ?: return "Clipboard service unavailable"
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return "Clipboard service unavailable"
            val clip = cm.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val txt = clip.getItemAt(0).text?.toString().orEmpty()
                if (txt.isNotBlank()) "Clipboard contains: '$txt', Sir." else "Your clipboard is currently empty, Sir."
            } else {
                "Your clipboard is currently empty, Sir."
            }
        } catch (e: Exception) {
            Log.e("ClipboardController", "Failed to read clipboard", e)
            "Clipboard unavailable, Sir."
        }
    }
}
