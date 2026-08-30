package com.jarvis.assistant.telemetry

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class DeviceTelemetryCollectorTest {

    private lateinit var mockContext: Context
    private lateinit var mockBatteryManager: BatteryManager
    private lateinit var mockConnectivityManager: ConnectivityManager
    private lateinit var mockAudioManager: AudioManager

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
        mockBatteryManager = mock(BatteryManager::class.java)
        mockConnectivityManager = mock(ConnectivityManager::class.java)
        mockAudioManager = mock(AudioManager::class.java)

        `when`(mockContext.getSystemService(Context.BATTERY_SERVICE)).thenReturn(mockBatteryManager)
        `when`(mockContext.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(mockConnectivityManager)
        `when`(mockContext.getSystemService(Context.AUDIO_SERVICE)).thenReturn(mockAudioManager)
    }

    @Test
    fun `getLiveTelemetry returns null values when context is null`() {
        val collector = DeviceTelemetryCollector(context = null)
        val telemetry = collector.getLiveTelemetry()

        assertNotNull(telemetry)
        assertNull(telemetry.batteryLevel)
        assertNull(telemetry.isCharging)
        assertNull(telemetry.networkType)
        assertNull(telemetry.volumeLevel)
        assertNull(telemetry.currentAudioOutput)
        assertTrue(telemetry.extraSensors.isEmpty())
    }

    @Test
    fun `getLiveTelemetry extracts battery level and isCharging correctly`() {
        `when`(mockBatteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)).thenReturn(87)
        `when`(mockBatteryManager.isCharging).thenReturn(true)

        val collector = DeviceTelemetryCollector(mockContext)
        val telemetry = collector.getLiveTelemetry()

        assertEquals(87, telemetry.batteryLevel)
        assertEquals(true, telemetry.isCharging)
    }

    @Test
    fun `getLiveTelemetry extracts battery discharging correctly`() {
        `when`(mockBatteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)).thenReturn(45)
        `when`(mockBatteryManager.isCharging).thenReturn(false)

        val collector = DeviceTelemetryCollector(mockContext)
        val telemetry = collector.getLiveTelemetry()

        assertEquals(45, telemetry.batteryLevel)
        assertEquals(false, telemetry.isCharging)
    }

    @Test
    fun `getLiveTelemetry extracts wifi network correctly`() {
        val mockNetwork = mock(Network::class.java)
        val mockCaps = mock(NetworkCapabilities::class.java)

        `when`(mockConnectivityManager.activeNetwork).thenReturn(mockNetwork)
        `when`(mockConnectivityManager.getNetworkCapabilities(mockNetwork)).thenReturn(mockCaps)
        `when`(mockCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)).thenReturn(true)
        `when`(mockCaps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)).thenReturn(false)

        val collector = DeviceTelemetryCollector(mockContext)
        val telemetry = collector.getLiveTelemetry()

        assertEquals("wifi", telemetry.networkType)
    }

    @Test
    fun `getLiveTelemetry extracts cellular network correctly`() {
        val mockNetwork = mock(Network::class.java)
        val mockCaps = mock(NetworkCapabilities::class.java)

        `when`(mockConnectivityManager.activeNetwork).thenReturn(mockNetwork)
        `when`(mockConnectivityManager.getNetworkCapabilities(mockNetwork)).thenReturn(mockCaps)
        `when`(mockCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)).thenReturn(false)
        `when`(mockCaps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)).thenReturn(true)

        val collector = DeviceTelemetryCollector(mockContext)
        val telemetry = collector.getLiveTelemetry()

        assertEquals("cellular", telemetry.networkType)
    }

    @Test
    fun `getLiveTelemetry reports offline when active network is null`() {
        `when`(mockConnectivityManager.activeNetwork).thenReturn(null)

        val collector = DeviceTelemetryCollector(mockContext)
        val telemetry = collector.getLiveTelemetry()

        assertEquals("offline", telemetry.networkType)
    }

    @Test
    fun `getLiveTelemetry extracts volume level percentage correctly`() {
        `when`(mockAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).thenReturn(15)
        `when`(mockAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC)).thenReturn(12)

        val collector = DeviceTelemetryCollector(mockContext)
        val telemetry = collector.getLiveTelemetry()

        assertEquals(80, telemetry.volumeLevel)
    }

    @Test
    fun `getLiveTelemetry handles zero max volume gracefully`() {
        `when`(mockAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).thenReturn(0)

        val collector = DeviceTelemetryCollector(mockContext)
        val telemetry = collector.getLiveTelemetry()

        assertNull(telemetry.volumeLevel)
    }

    @Test
    fun `getLiveTelemetry extracts bluetooth audio output`() {
        val mockDevice = mock(AudioDeviceInfo::class.java)
        `when`(mockDevice.type).thenReturn(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        `when`(mockAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)).thenReturn(arrayOf(mockDevice))

        val collector = DeviceTelemetryCollector(mockContext)
        val telemetry = collector.getLiveTelemetry()

        assertEquals("bluetooth", telemetry.currentAudioOutput)
    }

    @Test
    fun `getLiveTelemetry extracts wired headset audio output`() {
        val mockDevice = mock(AudioDeviceInfo::class.java)
        `when`(mockDevice.type).thenReturn(AudioDeviceInfo.TYPE_WIRED_HEADSET)
        `when`(mockAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)).thenReturn(arrayOf(mockDevice))

        val collector = DeviceTelemetryCollector(mockContext)
        val telemetry = collector.getLiveTelemetry()

        assertEquals("wired_headset", telemetry.currentAudioOutput)
    }

    @Test
    fun `getLiveTelemetry extracts speaker audio output`() {
        val mockDevice = mock(AudioDeviceInfo::class.java)
        `when`(mockDevice.type).thenReturn(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
        `when`(mockAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)).thenReturn(arrayOf(mockDevice))

        val collector = DeviceTelemetryCollector(mockContext)
        val telemetry = collector.getLiveTelemetry()

        assertEquals("speaker", telemetry.currentAudioOutput)
    }

    @Test
    fun `getLiveTelemetry handles exceptions in system services gracefully without throwing`() {
        `when`(mockContext.getSystemService(Context.BATTERY_SERVICE)).thenThrow(RuntimeException("SecurityException: Permission denied"))
        `when`(mockContext.getSystemService(Context.CONNECTIVITY_SERVICE)).thenThrow(RuntimeException("DeadSystemException"))
        `when`(mockContext.getSystemService(Context.AUDIO_SERVICE)).thenThrow(RuntimeException("RemoteException"))

        val collector = DeviceTelemetryCollector(mockContext)
        val telemetry = collector.getLiveTelemetry()

        assertNotNull(telemetry)
        assertNull(telemetry.batteryLevel)
        assertNull(telemetry.isCharging)
        assertNull(telemetry.networkType)
        assertNull(telemetry.volumeLevel)
        assertNull(telemetry.currentAudioOutput)
    }

    @Test
    fun `getLiveTelemetry serializes properly to JSONObject`() {
        `when`(mockBatteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)).thenReturn(90)
        `when`(mockBatteryManager.isCharging).thenReturn(true)

        val mockNetwork = mock(Network::class.java)
        val mockCaps = mock(NetworkCapabilities::class.java)
        `when`(mockConnectivityManager.activeNetwork).thenReturn(mockNetwork)
        `when`(mockConnectivityManager.getNetworkCapabilities(mockNetwork)).thenReturn(mockCaps)
        `when`(mockCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)).thenReturn(true)

        `when`(mockAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).thenReturn(10)
        `when`(mockAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC)).thenReturn(5)

        val mockDevice = mock(AudioDeviceInfo::class.java)
        `when`(mockDevice.type).thenReturn(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
        `when`(mockAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)).thenReturn(arrayOf(mockDevice))

        val collector = DeviceTelemetryCollector(mockContext)
        val telemetry = collector.getLiveTelemetry()
        val json = telemetry.toJsonObject()

        assertEquals(90, json.getInt("battery_level"))
        assertTrue(json.getBoolean("is_charging"))
        assertEquals("wifi", json.getString("network_type"))
        assertEquals(50, json.getInt("volume_level"))
        assertEquals("speaker", json.getString("current_audio_output"))
    }
}
