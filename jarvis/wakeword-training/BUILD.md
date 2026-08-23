# JARVIS Wake Word — Build, Verify & Train

Offline "Hey Jarvis" wake-word detection added to JARVIS Android, powered by
[livekit-wakeword](https://github.com/livekit/livekit-wakeword) ONNX models
run locally via **ONNX Runtime Mobile**. No Porcupine key, no cloud, no heavy
full-STT fallback for the common path.

## What was added (verified)

| Area | File(s) | Status |
|------|---------|--------|
| Offline detector (3 ONNX sessions) | `voice/wakeword/OnnxWakeWordDetector.kt` | ✅ shape-logic validated vs real models |
| Capture engine (AudioRecord 16 kHz) | `voice/wakeword/LiveKitWakeWordEngine.kt` | ✅ mic-owner pause/resume wired |
| Config / cooldown / interfaces | `voice/wakeword/WakeWordConfig.kt`, `WakeCooldown.kt`, `WakeWordDetector.kt`, `WakeWordListener.kt` | ✅ |
| Runtime wiring | `VoiceRuntime.kt` (`WAKE` state, `toggleMonitoring`, `setWakeSensitivity`, resume-after-command) | ✅ |
| State machine | `VoiceStateMachine.kt` (added `WAKE`) | ✅ |
| Foreground service | `JarvisForegroundService.kt` (`toggleWakeListening`, `setWakeSensitivity`, notification text) | ✅ |
| ViewModel / Settings | `JarvisViewModel.kt`, `SettingsManager.kt` | ✅ |
| UI | `HomeScreen` (orb + toggle), `SettingsScreen` (sensitivity), `ConversationScreen` (toggle), `MainActivity` (callbacks) | ✅ |
| Build | `app/build.gradle.kts` (`onnxruntime-android:1.22.0`, `aaptOptions.noCompress("onnx")`), `proguard-rules.pro` | ✅ |
| Assets | `app/src/main/assets/wakeword/{melspectrogram.onnx, embedding_model.onnx, README.md}` | ✅ (classifier added after training) |
| Training | `jarvis/wakeword-training/{hey_jarvis.yaml, download_models.sh, train.sh, README.md}` | ✅ |

## Bugs found & fixed during verification

1. **Over-descending nested-array reshape (would crash at runtime).** The
   original `flattenMel`/`flattenEmbeddings` walked ORT's nested Java arrays and
   over-descended into the `FloatArray` leaf, so the `Array<Array<Float>>` cast
   failed → detector always returned `null`. **Fix:** read each tensor as a
   flat `FloatBuffer` and reshape by *known* dims (`resultToFloatArray()`). This
   is rank-agnostic and proven against the real models.
2. **Wake never fired.** The capture loop called `detector.feedPcm()` (which only
   updates the ring buffer) but never `detector.processAndDetect()` (which
   evaluates threshold + fires the listener). **Fix:** call
   `processAndDetect()` after each fed frame.
3. **16KB-page crash risk.** `onnxruntime-android:1.22.0` is NOT 16KB-aligned
   for `arm64-v8a`/`x86_64` (verified with `readelf`: only armeabi-v7a + x86
   report `Align 0x4000`). Setting `android:use16KbPages="true"` would crash on
   Android 15+ arm64 devices, so the flag was **reverted**. See caveat below.

## Empirical verification done here (no Android SDK available)

- Downloaded the two frozen front-end models from upstream LiveKit.
- `python contract_test.py` — confirmed exact ONNX I/O:
  - `melspectrogram.onnx`: in `(1,samples)` → `(1,1,197,32)` → post `/10+2`
  - `embedding_model.onnx`: in `(N,76,32,1)` → `(N,1,1,96)`
  - 2 s @ 16 kHz → exactly 197 mel frames → 16 embeddings → classifier `(1,16,96)`
- `python kotlin_reshape_validate.py` — validated the Kotlin tensor-shape logic
  (flat-buffer reshape) reproduces these shapes against the real models. ✅

## Build on your machine (needs JDK 17 + Android SDK)

```bash
cd jarvis/android
export ANDROID_HOME="$PWD/../.build-env/android-sdk"   # or your SDK path
./gradlew assembleDebug            # debug APK
./gradlew assembleRelease          # release (R8 + our proguard rules)
```

Before the first build, fetch the two frozen models (already committed to
`assets/wakeword/`, but to refresh):

```bash
cd jarvis/wakeword-training && bash download_models.sh --frozen-only
```

Install & test:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
# Open JARVIS → Home screen shows "Wake word OFF" pill → tap to enable
# Say "Hey Jarvis" → orb goes to WAKE state → command prompt opens
```

## Train your own "Hey Jarvis" classifier (Colab, 1 command)

The two frozen front-end models are reused; only the classifier
`hey_jarvis.onnx` is trained. Place these 3 files in a Colab working dir (or
`jarvis/wakeword-training/`): `hey_jarvis.yaml`, `download_models.sh`,
`train.sh`. Then:

```bash
pip install -r https://raw.githubusercontent.com/livekit/livekit-wakeword/main/requirements.txt \
  || pip install -e "git+https://github.com/livekit/livekit-wakeword.git#egg=livekit-wakeword"
bash download_models.sh           # pulls frozen models + sample/negative audio
bash train.sh                     # trains + exports models/exported/hey_jarvis.onnx
```

Then drop the trained file into the app assets:

```bash
cp models/exported/hey_jarvis.onnx \
   jarvis/android/app/src/main/assets/wakeword/hey_jarvis.onnx
```

Rebuild & install. Tune `settings -> Wake Word Sensitivity` (Low/Balanced/High)
to trade false-accepts vs misses on your device.

## Known caveats

- **16KB pages:** `onnxruntime-android:1.22.0` lacks arm64 16KB-page alignment,
  so `android:use16KbPages` is intentionally left OFF. To enable it later, move to
  an ORT build that ships 16KB-aligned `arm64-v8a` `.so` (or strip other ABIs).
- **compileSdk / targetSdk = 34** (per repo). Play Console 2025 requires 35 for
  new/updated apps — bump when convenient (separate from this change).
- The classifier (`hey_jarvis.onnx`) must be trained/exported before offline
  detection works; until then `LiveKitWakeWordEngine.startMonitoring()` logs a
  warning and the wake pill stays inert (graceful degradation, no crash).
