package com.jarvis.assistant.actionengine.adapter

import android.content.Context
import android.util.Log
import com.jarvis.assistant.device.SmsController

class WhatsAppAdapter(private val context: Context?) {

    companion object {
        private const val TAG = "WhatsAppAdapter"
    }

    private val smsController = SmsController(context)

    fun sendWhatsAppMessage(contactName: String, message: String): Boolean {
        return try {
            val ok = smsController.sendWhatsApp(contactName, message)
            Log.i(TAG, "Sent WhatsApp message to $contactName: $ok")
            ok
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send WhatsApp message", e)
            false
        }
    }
}
