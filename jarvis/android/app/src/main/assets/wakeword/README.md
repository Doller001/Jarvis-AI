# Wake Word Assets

These three ONNX models power the offline "Hey Jarvis" detector
(`com.jarvis.assistant.voice.wakeword.LiveKitWakeWordEngine`).

| File | Source | Purpose |
|------|--------|---------|
| `melspectrogram.onnx` | livekit-wakeword (frozen) | 16 kHz PCM → mel spectrogram |
| `embedding_model.onnx` | livekit-wakeword (frozen) | 76-frame mel → 96-dim embedding |
| `hey_jarvis.onnx` | **trained by you** | (1,16,96) → wake score (1,1) |

The two frozen models ship with every livekit-wakeword install. Generate the
trained `hey_jarvis.onnx` from `jarvis/wakeword-training/`:

```bash
cd jarvis/wakeword-training
bash train.sh            # needs GPU
bash download_models.sh  # copies all 3 .onnx here
```

If this folder is missing `hey_jarvis.onnx`, the engine logs a warning and the
fallback text-matching path (Android SpeechRecognizer) is used instead — the
app still runs, just less efficiently.

Loaded at runtime via `onnxruntime-android` (see `build.gradle.kts`).
