package com.jarvis.assistant.voice

import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Automatically detects connected Bluetooth audio devices (headsets, earbuds, microphones)
 * and routes voice capture to the Bluetooth mic when connected, falling back to
 * the device microphone when disconnected.
 */
class AudioRouteManager(private val context: Context?) {

    companion object {
        private const val TAG = "AudioRouteManager"
    }

    private val audioManager: AudioManager? = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var isBluetoothScoActive = false
    private var isRegistered = false

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            Log.d(TAG, "AudioRoute Broadcast received: $action")
            when (action) {
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
                    if (state == BluetoothProfile.STATE_CONNECTED) {
                        Log.i(TAG, "Bluetooth headset connected — enabling Bluetooth mic routing")
                        enableBluetoothMic()
                    } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                        Log.i(TAG, "Bluetooth headset disconnected — reverting to built-in mic")
                        disableBluetoothMic()
                    }
                }
                AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> {
                    val scoState = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_DISCONNECTED)
                    Log.i(TAG, "SCO audio state: $scoState")
                    if (scoState == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                        isBluetoothScoActive = true
                    } else if (scoState == AudioManager.SCO_AUDIO_STATE_DISCONNECTED) {
                        isBluetoothScoActive = false
                    }
                }
            }
        }
    }

    fun start() {
        val ctx = context ?: return
        if (isRegistered) return
        try {
            val filter = IntentFilter().apply {
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            }
            ctx.registerReceiver(bluetoothReceiver, filter)
            isRegistered = true

            // Check if already connected
            if (isBluetoothHeadsetConnected()) {
                Log.i(TAG, "Bluetooth audio device already connected on startup — activating Bluetooth mic")
                enableBluetoothMic()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register audio route receiver", e)
        }
    }

    fun isBluetoothHeadsetConnected(): Boolean {
        val am = audioManager ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = am.getDevices(AudioManager.GET_DEVICES_INPUTS)
            return devices.any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            }
        }
        @Suppress("DEPRECATION")
        return am.isBluetoothScoAvailableOffCall || am.isBluetoothA2dpOn
    }

    fun enableBluetoothMic() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val inputDevices = am.availableCommunicationDevices
                val btDevice = inputDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                }
                if (btDevice != null) {
                    val success = am.setCommunicationDevice(btDevice)
                    Log.i(TAG, "setCommunicationDevice (Bluetooth): $success (${btDevice.productName})")
                    return
                }
            }

            @Suppress("DEPRECATION")
            if (am.isBluetoothScoAvailableOffCall) {
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                am.startBluetoothSco()
                am.isBluetoothScoOn = true
                isBluetoothScoActive = true
                Log.i(TAG, "Started Bluetooth SCO for voice capture")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling Bluetooth mic", e)
        }
    }

    fun disableBluetoothMic() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.clearCommunicationDevice()
            }
            @Suppress("DEPRECATION")
            if (isBluetoothScoActive) {
                am.isBluetoothScoOn = false
                am.stopBluetoothSco()
                am.mode = AudioManager.MODE_NORMAL
                isBluetoothScoActive = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling Bluetooth mic", e)
        }
    }

    fun release() {
        disableBluetoothMic()
        if (isRegistered) {
            try {
                context?.unregisterReceiver(bluetoothReceiver)
            } catch (_: Exception) {}
            isRegistered = false
        }
    }
}
