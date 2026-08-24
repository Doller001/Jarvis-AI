package com.jarvis.assistant.actionengine.adapter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

class YouTubeAdapter(private val context: Context?) {

    companion object {
        private const val TAG = "YouTubeAdapter"
        private const val PACKAGE_YOUTUBE = "com.google.android.youtube"
    }

    fun searchAndPlay(query: String): Boolean {
        val ctx = context ?: return false
        return try {
            val encoded = Uri.encode(query)
            val uri = Uri.parse("https://www.youtube.com/results?search_query=$encoded")
            try {
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage(PACKAGE_YOUTUBE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
                Log.i(TAG, "Opened YouTube search for query: $query")
                true
            } catch (_: Exception) {
                // Fallback to web browser if YouTube native app is not installed
                val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(browserIntent)
                Log.i(TAG, "Opened YouTube web search in browser: $query")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to search YouTube", e)
            false
        }
    }

    fun openDirectVideo(videoId: String): Boolean {
        val ctx = context ?: return false
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open video directly", e)
            false
        }
    }
}
