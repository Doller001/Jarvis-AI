package com.jarvis.assistant.device

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager
import android.provider.Settings
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SystemController(private val context: Context? = null) {

    fun toggleTorch(enable: Boolean): Boolean {
        Log.i("SystemController", "Toggling Torch -> $enable")
        return try {
            val ctx = context ?: return false
            val cameraManager = ctx.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val cameraId = cameraManager?.cameraIdList?.firstOrNull() ?: return false
            cameraManager.setTorchMode(cameraId, enable)
            true
        } catch (e: Exception) {
            Log.e("SystemController", "Failed to toggle torch", e)
            false
        }
    }

    fun setVolume(levelPercentage: Int): Boolean {
        Log.i("SystemController", "Setting volume to $levelPercentage%")
        return try {
            val ctx = context ?: return false
            val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val targetVolume = (maxVolume * (levelPercentage.coerceIn(0, 100) / 100.0)).toInt()
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, AudioManager.FLAG_SHOW_UI)
            true
        } catch (e: Exception) {
            Log.e("SystemController", "Failed to set volume", e)
            false
        }
    }

    fun toggleWifi(enable: Boolean): Boolean {
        Log.i("SystemController", "Toggling Wi-Fi -> $enable")
        return try {
            val ctx = context ?: return false
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e("SystemController", "Failed to open Wi-Fi settings", e)
            false
        }
    }

    fun toggleBluetooth(enable: Boolean): Boolean {
        Log.i("SystemController", "Toggling Bluetooth -> $enable")
        return try {
            val ctx = context ?: return false
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e("SystemController", "Failed to open Bluetooth settings", e)
            false
        }
    }

    fun getTime(): String {
        val sdf = SimpleDateFormat("HH:mm, EEEE, MMM d, yyyy", Locale.getDefault())
        return sdf.format(Date())
    }

    fun getBatteryLevel(): String {
        return try {
            val ctx = context ?: return "85% (Simulated)"
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 85
            "$level%"
        } catch (e: Exception) {
            "85%"
        }
    }

    fun getStorageInfo(): String {
        return try {
            val path = android.os.Environment.getDataDirectory()
            val stat = android.os.StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong
            val totalGB = (totalBlocks * blockSize) / (1024 * 1024 * 1024)
            val freeGB = (availableBlocks * blockSize) / (1024 * 1024 * 1024)
            "Storage: ${freeGB} GB free of ${totalGB} GB total"
        } catch (e: Exception) {
            "Storage status currently unavailable"
        }
    }

    fun openSettings(section: String? = null): Boolean {
        val ctx = context ?: return false
        val action = when (section?.lowercase()?.trim()) {
            "wifi", "wi-fi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display", "brightness" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound", "volume" -> Settings.ACTION_SOUND_SETTINGS
            "apps", "applications" -> Settings.ACTION_APPLICATION_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return try {
            val intent = Intent(action).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e("SystemController", "Failed to open settings section $section", e)
            false
        }
    }
}
