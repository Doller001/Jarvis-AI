package com.jarvis.assistant.device

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

enum class OemBrand {
    XIAOMI_HYPEROS,
    SAMSUNG_ONEUI,
    OPPO_REALME_COLOROS,
    VIVO_FUNTOUCH,
    HUAWEI_HONOR,
    ONEPLUS_OXYGEN,
    STOCK_ANDROID,
    UNKNOWN
}

/**
 * OEM-Aware Optimization & Background Survival Engine.
 * Handles aggressive OEM background task killers (HyperOS, OneUI, ColorOS, FuntouchOS)
 * to prevent Jarvis ForegroundService from being terminated in background.
 */
object OemOptimizer {
    private const val TAG = "OemOptimizer"

    fun detectOem(
        overrideManufacturer: String? = null,
        overrideBrand: String? = null
    ): OemBrand {
        val manufacturer = (overrideManufacturer ?: Build.MANUFACTURER.orEmpty()).lowercase()
        val brand = (overrideBrand ?: Build.BRAND.orEmpty()).lowercase()

        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") || brand.contains("xiaomi") ->
                OemBrand.XIAOMI_HYPEROS
            manufacturer.contains("samsung") || brand.contains("samsung") ->
                OemBrand.SAMSUNG_ONEUI
            manufacturer.contains("oppo") || manufacturer.contains("realme") || brand.contains("oppo") || brand.contains("realme") ->
                OemBrand.OPPO_REALME_COLOROS
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") || brand.contains("vivo") || brand.contains("iqoo") ->
                OemBrand.VIVO_FUNTOUCH
            manufacturer.contains("huawei") || manufacturer.contains("honor") || brand.contains("huawei") || brand.contains("honor") ->
                OemBrand.HUAWEI_HONOR
            manufacturer.contains("oneplus") || brand.contains("oneplus") ->
                OemBrand.ONEPLUS_OXYGEN
            manufacturer.contains("google") || manufacturer.contains("motorola") || manufacturer.contains("nokia") ->
                OemBrand.STOCK_ANDROID
            else ->
                OemBrand.UNKNOWN
        }
    }

    /**
     * Checks if standard Android battery optimization is ignored.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check battery optimization state", e)
            false
        }
    }

    /**
     * Generates the best-suited Intent to open the OEM's auto-start / background protection settings.
     */
    fun getOemAutoStartIntent(context: Context): Intent? {
        val packageName = context.packageName
        val oem = detectOem()

        val intents = when (oem) {
            OemBrand.XIAOMI_HYPEROS -> listOf(
                Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
                Intent().setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
            )
            OemBrand.OPPO_REALME_COLOROS -> listOf(
                Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
                Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")),
                Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"))
            )
            OemBrand.VIVO_FUNTOUCH -> listOf(
                Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
                Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
                Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.MainGuideActivity"))
            )
            OemBrand.HUAWEI_HONOR -> listOf(
                Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
                Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.bootstart.BootStartActivity"))
            )
            OemBrand.SAMSUNG_ONEUI -> listOf(
                Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")),
                Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.battery.ui.BatteryActivity"))
            )
            else -> emptyList()
        }

        for (intent in intents) {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (context.packageManager.resolveActivity(intent, 0) != null) {
                return intent
            }
        }

        // Standard Android battery optimization request fallback
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
    }
}
