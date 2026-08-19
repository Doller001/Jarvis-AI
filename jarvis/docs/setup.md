# Jarvis AI — Setup & Configuration Guide

---

## 📱 1. Android App Setup Flow

The Jarvis APK includes an interactive permission setup wizard:
1. **Microphone Permission**: Enables local wake-word detection and voice recognition.
2. **Notification Permission**: Enables the persistent status notification for `JarvisForegroundService`.
3. **Accessibility Service**: Enables UI tree navigation and screen automation (`JarvisAccessibilityService`).
4. **Battery Optimization Guidance**: Ensures background service is not killed by OEM battery savers.
5. **Optional Permissions**: Phone call, SMS, and camera permissions required for action tools.

---

## 🛢️ 2. Database & Memory Configuration

The Jarvis backend persists conversation memory and user facts:

### SQLite (Default)
```bash
export JARVIS_DB_PATH="jarvis_memory.db"
```

### PostgreSQL / Custom Database URL
```bash
export DATABASE_URL="postgresql://user:password@localhost:5432/jarvis_db"
```

---

## 🔨 3. Building the Android APK

```bash
cd jarvis/android

# Run unit test suite
./gradlew test

# Assemble Debug APK
./gradlew assembleDebug
```
Output APK location: `jarvis/android/app/build/outputs/apk/debug/app-debug.apk`
