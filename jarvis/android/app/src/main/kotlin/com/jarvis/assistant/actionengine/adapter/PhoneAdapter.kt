package com.jarvis.assistant.actionengine.adapter

import android.content.Context
import android.util.Log
import com.jarvis.assistant.device.CallController
import com.jarvis.assistant.device.CallLogController

class PhoneAdapter(private val context: Context?) {

    companion object {
        private const val TAG = "PhoneAdapter"
    }

    private val callController = CallController(context)
    private val callLogController = CallLogController(context)

    fun makeCall(contactOrNumber: String): Boolean {
        return try {
            val ok = callController.makeCall(contactOrNumber)
            Log.i(TAG, "Initiated call to $contactOrNumber: $ok")
            ok
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating call", e)
            false
        }
    }

    fun getRecentCalls(): String {
        return try {
            val calls = callLogController.getRecentCalls(5)
            if (calls.isNotEmpty()) {
                calls.joinToString("\n")
            } else {
                "No recent calls found."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading call log", e)
            "Call log access error"
        }
    }
}
