package com.jarvis.assistant.device

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log

data class AppInfo(
    val name: String,
    val packageName: String,
    val isSystem: Boolean,
    val category: String
)

class AppController(private val context: Context? = null) {

    companion object {
        private const val TAG = "AppController"
    }

    fun getAllInstalledApps(): List<AppInfo> {
        val ctx = context ?: return emptyList()
        val pm = ctx.packageManager
        return try {
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            packages.mapNotNull { appInfo ->
                val name = pm.getApplicationLabel(appInfo).toString()
                if (name.isBlank()) return@mapNotNull null
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val category = getAppCategory(appInfo.packageName)
                AppInfo(name = name, packageName = appInfo.packageName, isSystem = isSystem, category = category)
            }.sortedBy { it.name.lowercase() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enumerate installed apps", e)
            emptyList()
        }
    }

    fun getAppsByCategory(category: String): List<AppInfo> {
        val target = category.lowercase().trim()
        return getAllInstalledApps().filter { it.category == target }
    }

    fun searchApps(query: String): List<AppInfo> {
        val q = query.lowercase().trim()
        return getAllInstalledApps().filter {
            it.name.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
    }

    fun launchApp(appName: String): Boolean {
        Log.i(TAG, "Jarvis launching app: '$appName'")
        val ctx = context ?: return false
        val pm = ctx.packageManager

        try {
            // 1. Direct package name match
            var launchIntent: Intent? = pm.getLaunchIntentForPackage(appName)

            if (launchIntent == null) {
                // 2. Search installed applications by display label or package
                val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                val targetApp = installedApps.firstOrNull { appInfo ->
                    val label = pm.getApplicationLabel(appInfo).toString().lowercase()
                    label.contains(appName.lowercase()) || appName.lowercase().contains(label) ||
                            appInfo.packageName.lowercase().contains(appName.lowercase())
                }

                if (targetApp != null) {
                    launchIntent = pm.getLaunchIntentForPackage(targetApp.packageName)
                }
            }

            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(launchIntent)
                Log.i(TAG, "Successfully launched app '$appName'")
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch app '$appName'", e)
        }

        Log.w(TAG, "Could not find installed app matching '$appName'")
        return false
    }

    fun getAppCategory(packageName: String): String {
        return when {
            // Social
            packageName in listOf(
                "com.instagram.android", "com.facebook.katana", "com.facebook.orca",
                "com.snapchat", "com.twitter.android", "com.zhiliaoapp.musically"
            ) -> "social"

            // Communication
            packageName in listOf(
                "com.whatsapp", "com.whatsapp.w4b", "org.telegram", "com.google.android.apps.tachyon",
                "com.samsung.android.messaging", "com.google.android.apps.messaging"
            ) -> "communication"

            // Music / Audio
            packageName in listOf(
                "com.sec.android.app.music", "com.google.android.apps.youtube.music",
                "com.spotify.music", "com.gaana", "com.jiosaavn.android", "com.wynkmusic",
                "com.ymusic", "com.mxtech.videoplayerpro", "org.videolan.vlc", "com.mxtech.videoplayer.ad"
            ) || packageName.contains("music") -> "music"

            // AI Assistants
            packageName in listOf(
                "com.openai.chatgpt", "com.google.android.apps.ai",
                "com.anthropic.claude", "com.perplexity.app"
            ) -> "ai"

            // Games
            packageName in listOf(
                "com.samsung.android.gaminghub", "com.samsung.android.gamelauncher",
                "com.tinder.test_live", "com.dts.freefireth", "com.pubg.imobile"
            ) || packageName.contains("game") -> "games"

            // Productivity
            packageName in listOf(
                "com.samsung.android.notes", "com.google.android.keep",
                "com.google.android.apps.docs", "com.github.android",
                "com.termux", "com.zarchiver", "com.sec.android.calculator",
                "com.samsung.android.calendar", "com.google.android.calendar"
            ) -> "productivity"

            // Browser
            packageName in listOf(
                "com.android.chrome", "com.sec.android.app.sbrowser",
                "com.opera.browser", "com.ucbrowser.demo", "org.mozilla.firefox"
            ) -> "browser"

            // Media / Photos / Videos
            packageName in listOf(
                "com.google.android.apps.photos", "com.sec.android.gallery3d",
                "com.sec.android.app.videoplayer", "com.google.android.youtube"
            ) || packageName.contains("gallery") -> "media"

            // Telecom
            packageName.startsWith("com.jio") || packageName.contains("airtel") || packageName.contains("myvodafone") -> "telecom"

            else -> "general"
        }
    }
}
