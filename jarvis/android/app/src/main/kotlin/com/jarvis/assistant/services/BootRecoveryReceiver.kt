package com.jarvis.assistant.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i("BootRecoveryReceiver", "Device reboot detected. Restoring Jarvis lightweight recovery state...")
        }
    }
}
