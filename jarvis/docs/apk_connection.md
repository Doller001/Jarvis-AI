# Connecting Jarvis Android APK to Backend

This guide explains how to connect the Jarvis Android Application (APK) to the Jarvis Python Backend across different environments: Android Emulator, Local Physical Device over Wi-Fi, and Cloud Deployments (Render / Docker).

---

## 🌐 Environment Network Matrix

| Deployment Environment | HTTP API Base URL | WebSocket Realtime URL |
|---|---|---|
| **Android Emulator** (Local PC) | `http://10.0.2.2:8000` | `ws://10.0.2.2:8000/ws` |
| **Physical Android Device** (Same Wi-Fi) | `http://<YOUR_PC_LOCAL_IP>:8000` | `ws://<YOUR_PC_LOCAL_IP>:8000/ws` |
| **Render Cloud / Production** | `https://jarvis-backend.onrender.com` | `wss://jarvis-backend.onrender.com/ws` |
| **Custom VPS / Docker** | `https://your-domain.com` | `wss://your-domain.com/ws` |

---

## 1. Configuring URLs in Android Source Code

### Option A: Direct Configuration in `ApiClient.kt` & `WebSocketClient.kt`

In `jarvis/android/app/src/main/kotlin/com/jarvis/assistant/network/ApiClient.kt`:
```kotlin
// Android Emulator default: http://10.0.2.2:8000
// Cloud Production default: https://jarvis-backend.onrender.com
class ApiClient(val baseUrl: String = "https://jarvis-backend.onrender.com") { ... }
```

In `jarvis/android/app/src/main/kotlin/com/jarvis/assistant/network/WebSocketClient.kt`:
```kotlin
// Android Emulator default: ws://10.0.2.2:8000/ws
// Cloud Production default: wss://jarvis-backend.onrender.com/ws
class WebSocketClient(
    val wsUrl: String = "wss://jarvis-backend.onrender.com/ws",
    private val connectionManager: ConnectionManager = ConnectionManager()
) { ... }
```

---

## 2. Testing Connection on Physical Android Devices

1. **Find your computer's Local IP Address**:
   - **Linux / macOS**: `hostname -I` or `ifconfig` (e.g. `192.168.1.50`)
   - **Windows**: `ipconfig` (Look for `IPv4 Address`, e.g. `192.168.1.50`)

2. **Ensure port 8000 is open in Firewall**:
   - **Linux (UFW)**: `sudo ufw allow 8000/tcp`
   - **Windows Firewall**: Allow Inbound rule for Port `8000`.

3. **Update URLs in App**:
   - Set `baseUrl` to `http://192.168.1.50:8000`
   - Set `wsUrl` to `ws://192.168.1.50:8000/ws`

---

## 3. Cleartext Traffic for Local Testing (`http://`)

Android 9+ (API 28+) blocks unencrypted HTTP traffic by default. The `AndroidManifest.xml` supports local network connections:

```xml
<application
    android:usesCleartextTraffic="true" ...>
```
*(For production releases using HTTPS/WSS on Render, cleartext traffic is automatically secured over SSL).*
