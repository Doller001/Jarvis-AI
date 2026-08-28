#!/bin/bash
# Jarvis Assistant - Live Monitor Script
# Usage: bash live-monitor.sh [option]

APK_PATH="/home/shanu/Desktop/and9/jarvis-debug.apk"
PACKAGE="com.jarvis.assistant.debug"
ACTIVITY="com.jarvis.assistant.debug/com.jarvis.assistant.ui.MainActivity"

case "$1" in
    install)
        echo "Installing APK..."
        adb install -r -g "$APK_PATH"
        ;;
    launch)
        echo "Launching Jarvis..."
        adb shell am start -n "$ACTIVITY"
        ;;
    logcat)
        echo "Starting live logcat for Jarvis..."
        PID=$(adb shell pidof -s "$PACKAGE" 2>/dev/null)
        if [ -n "$PID" ]; then
            echo "Monitoring process PID: $PID (Package: $PACKAGE)"
            adb logcat -v color --pid="$PID"
        else
            echo "Jarvis is not running yet. Filtering all Jarvis tags & runtime errors..."
            adb logcat -v color -s \
                "Jarvis:*" "JarvisVM:*" "VoiceRuntime:*" "WakeWord:*" "MemoryEngine:*" \
                "IntentResolver:*" "SpeechController:*" "JarvisAccessibility:*" \
                "AccessibilityController:*" "ScreenInspector:*" "GestureController:*" \
                "AlarmController:*" "LocationController:*" "MediaController:*" \
                "AppController:*" "ApiClient:*" "BackendHealthManager:*" \
                "AudioRouteManager:*" "VadEngine:*" "NearFieldProcessor:*" \
                "LowLatencyAudioCapture:*" "OnnxWakeWordDetector:*" "LiveKitWakeWordEngine:*" \
                "JarvisMemoryDb:*" "MemoryRouter:*" "VoiceDiagnostics:*" \
                "AudioSessionManager:*" "TextToSpeechEngine:*" "VoiceStateMachine:*" \
                "DiagnosticEventBus:*" "JarvisOverlayService:*" "TaskExecutionCoordinator:*" \
                "JarvisForegroundService:*" "AndroidRuntime:E" "*:S"
        fi
        ;;
    logcat-all)
        echo "Starting full logcat..."
        adb logcat -v color
        ;;
    device)
        echo "Checking connected devices..."
        adb devices -l
        ;;
    install-launch)
        echo "Installing and launching..."
        adb install -r -g "$APK_PATH" && adb shell am start -n "$ACTIVITY"
        sleep 2
        echo "Starting logcat..."
        adb logcat -v threadtime -s "Jarvis:*" "JarvisVM:*" "VoiceRuntime:*" "WakeWord:*" "MemoryEngine:*"
        ;;
    clear)
        echo "Clearing logcat buffer..."
        adb logcat -c
        ;;
    crash)
        echo "Monitoring crashes..."
        adb logcat -v threadtime *:E | grep -i "FATAL\|crash\|ANR\|AndroidRuntime"
        ;;
    kill)
        echo "Force stopping Jarvis..."
        adb shell am force-stop "$PACKAGE"
        ;;
    uninstall)
        echo "Uninstalling Jarvis..."
        adb uninstall "$PACKAGE"
        ;;
    shell)
        echo "Opening adb shell..."
        adb shell
        ;;
    *)
        echo "========================================="
        echo "  Jarvis Assistant - Live Monitor"
        echo "========================================="
        echo ""
        echo "Usage: bash live-monitor.sh [command]"
        echo ""
        echo "Commands:"
        echo "  install         - Install APK to device"
        echo "  launch          - Launch the app"
        echo "  install-launch  - Install + Launch + Start logcat"
        echo "  logcat          - Live logs (Jarvis filtered)"
        echo "  logcat-all      - Full logcat (all apps)"
        echo "  crash           - Monitor crashes only"
        echo "  device          - List connected devices"
        echo "  clear           - Clear logcat buffer"
        echo "  kill            - Force stop the app"
        echo "  uninstall       - Uninstall the app"
        echo "  shell           - Open adb shell"
        echo ""
        echo "Examples:"
        echo "  bash live-monitor.sh install-launch"
        echo "  bash live-monitor.sh logcat"
        echo "  bash live-monitor.sh crash"
        echo ""
        echo "APK: $APK_PATH"
        echo "Package: $PACKAGE"
        echo "========================================="
        ;;
esac
