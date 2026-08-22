package com.jarvis.assistant.device

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import com.jarvis.assistant.accessibility.AccessibilityController

data class AppInfo(
    val name: String,
    val packageName: String,
    val isSystem: Boolean,
    val category: String
)

class AppController(private val context: Context? = null) {

    companion object {
        private const val TAG = "AppController"

        private val APP_ALIASES = mapOf(
            "youtube" to listOf("com.google.android.youtube", "com.google.android.youtube.tv"),
            "yt" to listOf("com.google.android.youtube"),
            "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
            "chrome" to listOf("com.android.chrome", "org.chromium.chrome"),
            "browser" to listOf("com.android.chrome", "com.sec.android.app.sbrowser", "org.mozilla.firefox"),
            "camera" to listOf("com.sec.android.app.camera", "com.google.android.GoogleCamera", "com.android.camera"),
            "gallery" to listOf("com.sec.android.gallery3d", "com.google.android.apps.photos", "com.android.gallery3d"),
            "photos" to listOf("com.google.android.apps.photos", "com.sec.android.gallery3d"),
            "calculator" to listOf("com.sec.android.app.popupcalculator", "com.google.android.calculator", "com.android.calculator2"),
            "spotify" to listOf("com.spotify.music"),
            "instagram" to listOf("com.instagram.android"),
            "telegram" to listOf("org.telegram.messenger", "org.telegram.messenger.web"),
            "settings" to listOf("com.android.settings"),
            "clock" to listOf("com.sec.android.app.clockpackage", "com.google.android.deskclock"),
            "alarm" to listOf("com.sec.android.app.clockpackage", "com.google.android.deskclock"),
            "maps" to listOf("com.google.android.apps.maps"),
            "contacts" to listOf("com.samsung.android.app.contacts", "com.google.android.contacts"),
            "phone" to listOf("com.samsung.android.dialer", "com.google.android.dialer"),
            "dialer" to listOf("com.samsung.android.dialer", "com.google.android.dialer"),
            "messages" to listOf("com.samsung.android.messaging", "com.google.android.apps.messaging"),
            "sms" to listOf("com.samsung.android.messaging", "com.google.android.apps.messaging"),
            "gmail" to listOf("com.google.android.gm"),
            "play store" to listOf("com.android.vending"),
            "playstore" to listOf("com.android.vending"),
            "netflix" to listOf("com.netflix.ninja", "com.netflix.mediaclient"),
            "music" to listOf("com.sec.android.app.music", "com.vivo.music", "com.google.android.apps.youtube.music", "com.spotify.music", "com.jio.media.jiobeats"),
            "samsung music" to listOf("com.sec.android.app.music"),
            "notes" to listOf("com.samsung.android.app.notes", "com.google.android.keep", "com.vivo.notes"),
            "files" to listOf("com.sec.android.app.myfiles", "com.google.android.apps.nbu.files", "com.android.documentsui", "com.vivo.FileManager"),
            "my files" to listOf("com.sec.android.app.myfiles", "com.google.android.apps.nbu.files"),
            "calendar" to listOf("com.samsung.android.calendar", "com.google.android.calendar", "com.bbk.calendar"),
            "weather" to listOf("com.sec.android.daemonapp", "com.google.android.googlequicksearchbox", "com.vivo.weather"),
            "video" to listOf("com.sec.android.app.videoplayer", "org.videolan.vlc", "com.vivo.video")
        )
    }

    private val accessibilityController = AccessibilityController()

    fun getAllInstalledApps(): List<AppInfo> {
        val ctx = context ?: return emptyList()
        val pm = ctx.packageManager
        return try {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val pkgAppsList: List<ResolveInfo> = pm.queryIntentActivities(mainIntent, 0)
            val list = pkgAppsList.mapNotNull { resolveInfo ->
                val name = resolveInfo.loadLabel(pm).toString()
                val pkgName = resolveInfo.activityInfo.packageName
                if (name.isBlank()) return@mapNotNull null
                val isSystem = (resolveInfo.activityInfo.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                val category = getAppCategory(pkgName)
                AppInfo(name = name, packageName = pkgName, isSystem = isSystem, category = category)
            }
            if (list.isNotEmpty()) list.distinctBy { it.packageName }.sortedBy { it.name.lowercase() }
            else {
                // Fallback to getInstalledApplications
                val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                packages.mapNotNull { appInfo ->
                    val name = pm.getApplicationLabel(appInfo).toString()
                    if (name.isBlank()) return@mapNotNull null
                    val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                    AppInfo(name = name, packageName = appInfo.packageName, isSystem = isSystem, category = getAppCategory(appInfo.packageName))
                }.sortedBy { it.name.lowercase() }
            }
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
        val target = appName.lowercase().trim()
        Log.i(TAG, "Jarvis launching app: '$target'")
        val ctx = context ?: return false
        val pm = ctx.packageManager

        try {
            // 1. Check known aliases
            APP_ALIASES[target]?.let { candidatePackages ->
                for (pkg in candidatePackages) {
                    val intent = pm.getLaunchIntentForPackage(pkg)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(intent)
                        Log.i(TAG, "Launched alias app package: $pkg")
                        return true
                    }
                }
            }

            // 2. Direct package name match
            val directIntent = pm.getLaunchIntentForPackage(target)
            if (directIntent != null) {
                directIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(directIntent)
                Log.i(TAG, "Launched direct package: $target")
                return true
            }

            // 3. Search via launcher activities
            val allApps = getAllInstalledApps()
            val matchedApp = allApps.firstOrNull { app ->
                val appLabel = app.name.lowercase()
                appLabel == target || appLabel.contains(target) || target.contains(appLabel) ||
                        app.packageName.lowercase().contains(target)
            }

            if (matchedApp != null) {
                val launchIntent = pm.getLaunchIntentForPackage(matchedApp.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(launchIntent)
                    Log.i(TAG, "Launched matched app '${matchedApp.name}' (${matchedApp.packageName})")
                    return true
                }
            }

            // 4. Intent Actions for core utilities
            if (target.contains("camera")) {
                val camIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(camIntent)
                return true
            }
            if (target.contains("setting")) {
                val settingsIntent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(settingsIntent)
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch app '$appName'", e)
        }

        Log.w(TAG, "Could not find installed app matching '$appName'")
        return false
    }

    fun closeApp(appName: String? = null): Boolean {
        Log.i(TAG, "Jarvis closing app: '${appName ?: "current"}'")
        val ctx = context

        var killedProcess = false
        if (!appName.isNullOrBlank() && ctx != null) {
            val target = appName.lowercase().trim()
            val allApps = getAllInstalledApps()
            val matchedApp = allApps.firstOrNull { app ->
                val appLabel = app.name.lowercase()
                appLabel == target || appLabel.contains(target) || target.contains(appLabel) ||
                        app.packageName.lowercase().contains(target)
            }
            if (matchedApp != null) {
                try {
                    val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    am?.killBackgroundProcesses(matchedApp.packageName)
                    killedProcess = true
                    Log.i(TAG, "Killed background process for ${matchedApp.packageName}")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not kill process: ${e.message}")
                }
            }
        }

        // Return to Home Screen via Accessibility and/or Intent
        val accessibilityHome = accessibilityController.home()
        if (ctx != null) {
            try {
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(homeIntent)
            } catch (_: Exception) {}
        }

        return accessibilityHome || killedProcess || true
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
                "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "com.google.android.apps.tachyon",
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
                "com.dts.freefireth", "com.pubg.imobile"
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
                "com.opera.browser", "org.mozilla.firefox"
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
