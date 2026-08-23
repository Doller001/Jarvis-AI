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
import androidx.core.content.ContextCompat

/**
 * Automatically detects connected Bluetooth audio devices and routes voice capture
 * to the Bluetooth mic ONLY during active listening sessions, immediately restoring
 * [AudioManager.MODE_NORMAL] and clearing communication devices when idle or in background.
 *
 * This prevents background music/media from being ducked or degraded into narrow-band VoIP mode.
 */
class AudioRouteManager(private val context: Context?) {

    companion object {
        private const val TAG = "AudioRouteManager"
    }

    private val audioManager: AudioManager? = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var isBluetoothScoActive = false
    private var isRegistered = false
    private var isVoiceRoutingActive = false

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            Log.d(TAG, "AudioRoute Broadcast received: $action")
            when (action) {
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
                    if (state == BluetoothProfile.STATE_CONNECTED) {
                        Log.i(TAG, "Bluetooth headset connected (available for listening)")
                        if (isVoiceRoutingActive) {
                            enableBluetoothMic()
                        }
                    } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                        Log.i(TAG, "Bluetooth headset disconnected")
                        disableBluetoothMic()
                    }
                }
                AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> {
                    val scoState = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_DISCONNECTED)
                    Log.i(TAG, "SCO audio state: $scoState")
                    isBluetoothScoActive = (scoState == AudioManager.SCO_AUDIO_STATE_CONNECTED)
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
            ContextCompat.registerReceiver(
                ctx,
                bluetoothReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            isRegistered = true
            // Ensure audio mode starts strictly normal so background media is never attenuated
            ensureNormalAudioMode()
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
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            }
        }
        @Suppress("DEPRECATION")
        return am.isBluetoothScoAvailableOffCall
    }

    /**
     * Called when VoiceRuntime enters LISTENING state.
     */
    fun activateVoiceRouting() {
        isVoiceRoutingActive = true
        if (isBluetoothHeadsetConnected()) {
            enableBluetoothMic()
        }
    }

    /**
     * Called when VoiceRuntime exits LISTENING state (to IDLE, PROCESSING, SPEAKING, ERROR).
     * Immediately restores MODE_NORMAL and releases communication devices.
     */
    fun deactivateVoiceRouting() {
        isVoiceRoutingActive = false
        disableBluetoothMic()
        ensureNormalAudioMode()
    }

    private fun enableBluetoothMic() {
        val am = audioManager ?: return
        if (!isBluetoothHeadsetConnected()) {
            Log.d(TAG, "No Bluetooth microphone device present — skipping SCO enable")
            return
        }
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

    private fun disableBluetoothMic() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.clearCommunicationDevice()
            }
            @Suppress("DEPRECATION")
            if (isBluetoothScoActive || am.isBluetoothScoOn) {
                am.isBluetoothScoOn = false
                am.stopBluetoothSco()
                isBluetoothScoActive = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling Bluetooth mic", e)
        }
    }

    fun ensureNormalAudioMode() {
        val am = audioManager ?: return
        try {
            if (am.mode != AudioManager.MODE_NORMAL) {
                am.mode = AudioManager.MODE_NORMAL
                Log.d(TAG, "Restored AudioManager mode to MODE_NORMAL")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed resetting audio mode to MODE_NORMAL", e)
        }
    }

    fun release() {
        deactivateVoiceRouting()
        if (isRegistered) {
            try {
                context?.unregisterReceiver(bluetoothReceiver)
            } catch (_: Exception) {}
            isRegistered = false
        }
    }
}
