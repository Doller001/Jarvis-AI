# Jarvis AI — Setup & Installation Guide

## 1. Android App Setup Flow

The Jarvis APK includes an interactive permission setup flow:
1. **Microphone Permission**: Enables local wake-word detection and voice recognition.
2. **Notification Permission**: Enables the persistent status notification for `JarvisForegroundService`.
3. **Accessibility Service**: Enables UI tree navigation and screen automation.
4. **Battery Optimization Guidance**: Ensures background service is not killed by OEM battery savers.
5. **Optional Phone / SMS / Camera Permissions**: Required only when using phone calls, SMS, or camera features.

## 2. Building the APK

```bash
cd android
./gradlew assembleDebug
```
Output APK location: `android/app/build/outputs/apk/debug/app-debug.apk`

## 3. Running Android Unit Tests

```bash
cd android
./gradlew test
```
