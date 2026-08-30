package com.jarvis.assistant.telemetry

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.util.Log
import com.jarvis.assistant.network.SensoryTelemetryPayload

/**
 * Collects live device sensory and hardware telemetry (battery, network, volume, audio routing).
 * All queries are wrapped safely in try-catch to guarantee no crashes on missing permissions or OEM differences.
 */
class DeviceTelemetryCollector(private val context: Context?) {

    companion object {
        private const val TAG = "DeviceTelemetryCollector"
    }

    /**
     * Gathers a snapshot of live device telemetry.
     */
    fun getLiveTelemetry(): SensoryTelemetryPayload {
        val (batteryLevel, isCharging) = getBatteryInfo()
        val networkType = getNetworkType()
        val volumeLevel = getVolumeLevel()
        val audioOutput = getAudioOutput()

        return SensoryTelemetryPayload(
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            networkType = networkType,
            volumeLevel = volumeLevel,
            currentAudioOutput = audioOutput,
            extraSensors = emptyMap()
        )
    }

    private fun getBatteryInfo(): Pair<Int?, Boolean?> {
        return try {
            val ctx = context ?: return Pair(null, null)
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return Pair(null, null)

            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val batteryLevel = if (level in 0..100) level else null

            val isCharging = try {
                bm.isCharging
            } catch (_: Throwable) {
                try {
                    val status = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
                    status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                } catch (_: Throwable) {
                    null
                }
            }

            Pair(batteryLevel, isCharging)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to read battery telemetry: ${e.message}")
            Pair(null, null)
        }
    }

    private fun getNetworkType(): String? {
        return try {
            val ctx = context ?: return null
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
            val activeNetwork = cm.activeNetwork ?: return "offline"
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return "offline"

            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "bluetooth"
                !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> "offline"
                else -> "offline"
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to read network telemetry: ${e.message}")
            null
        }
    }

    private fun getVolumeLevel(): Int? {
        return try {
            val ctx = context ?: return null
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
            val maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (maxVolume > 0) {
                val currentVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                ((currentVolume.toDouble() / maxVolume.toDouble()) * 100).toInt().coerceIn(0, 100)
            } else {
                null
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to read volume telemetry: ${e.message}")
            null
        }
    }

    private fun getAudioOutput(): String? {
        return try {
            val ctx = context ?: return null
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null

            val devices = try {
                am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            } catch (_: Throwable) {
                null
            }

            if (devices.isNullOrEmpty()) {
                @Suppress("DEPRECATION")
                return when {
                    am.isBluetoothA2dpOn || am.isBluetoothScoOn -> "bluetooth"
                    am.isWiredHeadsetOn -> "wired_headset"
                    else -> null
                }
            }

            val types = devices.map { it.type }.toSet()
            when {
                types.any { isBluetoothAudioDevice(it) } -> "bluetooth"
                types.any { isWiredAudioDevice(it) } -> "wired_headset"
                types.any { isSpeakerAudioDevice(it) } -> "speaker"
                else -> "speaker"
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to read audio output telemetry: ${e.message}")
            null
        }
    }

    private fun isBluetoothAudioDevice(type: Int): Boolean {
        return when (type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST,
            AudioDeviceInfo.TYPE_HEARING_AID -> true
            else -> false
        }
    }

    private fun isWiredAudioDevice(type: Int): Boolean {
        return when (type) {
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL,
            AudioDeviceInfo.TYPE_AUX_LINE -> true
            else -> false
        }
    }

    private fun isSpeakerAudioDevice(type: Int): Boolean {
        return when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> true
            else -> false
        }
    }
}
