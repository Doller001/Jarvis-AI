package com.jarvis.assistant.device

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log

class AppController(private val context: Context? = null) {

    fun launchApp(appName: String): Boolean {
        Log.i("AppController", "Jarvis launching app: '$appName'")
        val ctx = context ?: return false
        val pm = ctx.packageManager

        try {
            // First check direct package name match
            var launchIntent: Intent? = pm.getLaunchIntentForPackage(appName)

            if (launchIntent == null) {
                // Find installed application by matching display label
                val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                val targetApp = installedApps.firstOrNull { appInfo ->
                    val label = pm.getApplicationLabel(appInfo).toString().lowercase()
                    label.contains(appName.lowercase()) || appName.lowercase().contains(label)
                }

                if (targetApp != null) {
                    launchIntent = pm.getLaunchIntentForPackage(targetApp.packageName)
                }
            }

            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(launchIntent)
                Log.i("AppController", "Successfully launched app '$appName'")
                return true
            }
        } catch (e: Exception) {
            Log.e("AppController", "Failed to launch app '$appName'", e)
        }

        Log.w("AppController", "Could not find installed app matching '$appName'")
        return false
    }
}
