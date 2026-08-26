# JARVIS / AND9 — Diagnostic Baseline & Test Metrics

## 1. System Inventory Baseline
- **Android App**: Kotlin 1.9.22 / Android SDK 34 / Jetpack Compose / ONNX Runtime Mobile 1.17.0.
- **Backend**: Python 3.11+ / FastAPI / WebSockets / Pydantic / AsyncIO / Uvicorn.
- **Artifacts**: Exported APK `export/jarvis-production-release.apk` (~34MB).

## 2. Telemetry Measurements
- **Wake Word Precision**: OnnxWakeWordDetector temporal gate ($3/5$ hits threshold, sensitivity calibrated).
- **Wake-to-STT Transition Latency**:
  - *Previous Baseline*: $1700\text{ ms}$ (blocked by "Yes boss" TTS playback).
  - *Hardened Metric*: $\le 100\text{ ms}$ (earcon tone + instant STT acquisition).
- **Processing Watchdog**:
  - *Previous Baseline*: $75.8\text{ seconds}$ unhandled hang on unverified commands.
  - *Hardened Metric*: $6000\text{ ms}$ strict timeout watchdog with auto-recovery to `WAKE_LISTENING`.
- **Command Verification**:
  - *Previous Baseline*: Incomplete step ACK.
  - *Hardened Metric*: $100\%$ verification requirement with step evidence before completion acknowledgement.

## 3. Test Baseline
- **Backend PyTest Suite**: 36 Passed / 0 Failed.
- **Android JUnit Suite**: 67 Passed / 0 Failed.
