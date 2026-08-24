package com.jarvis.assistant.actionengine.adapter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

class ChromeAdapter(private val context: Context?) {

    companion object {
        private const val TAG = "ChromeAdapter"
        private const val PACKAGE_CHROME = "com.android.chrome"
    }

    fun openUrlOrSearch(query: String): Boolean {
        val ctx = context ?: return false
        return try {
            val url = if (query.startsWith("http://") || query.startsWith("https://")) {
                query
            } else {
                "https://www.google.com/search?q=" + Uri.encode(query)
            }
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    setPackage(PACKAGE_CHROME)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
                Log.i(TAG, "Opened Chrome URL/Search: $url")
                true
            } catch (_: Exception) {
                // Fallback to default browser if Chrome is not installed
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(fallbackIntent)
                Log.i(TAG, "Opened default browser URL/Search: $url")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open URL/Search in browser", e)
            false
        }
    }
}
