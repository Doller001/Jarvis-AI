# JARVIS Wake Word — Training & Model Delivery

Custom "Hey Jarvis" / "Jarvis" wake word for the JARVIS Android assistant,
built on [livekit-wakeword](https://github.com/livekit/livekit-wakeword).

## Why this approach

LiveKit wakeword has **no native Android runtime**, but its whole pipeline is
ONNX. The on-device detector runs three ONNX models via **ONNX Runtime Mobile**
(`onnxruntime-android`) — fully offline, no Porcupine key, no heavy STT
fallback:

| Stage | Model | Input → Output |
|-------|-------|----------------|
| 1. Front-end (frozen) | `melspectrogram.onnx` | 16 kHz PCM → mel spectrogram (32-dim) |
| 2. Front-end (frozen) | `embedding_model.onnx` | 76-frame mel window → 96-dim embedding (stride 8) |
| 3. Classifier (custom) | `hey_jarvis.onnx` | `(1,16,96)` embeddings → sigmoid score `(1,1)` |

Models 1 & 2 are **identical for every wake word** — download them once and
ship them in the APK. Model 3 is your trained "Hey Jarvis" classifier.

## Train (one command, needs a GPU)

Run on Google Colab / a cloud GPU / a Linux box with an NVIDIA card + ffmpeg:

```bash
git clone <your-jarvis-repo> && cd jarvis/wakeword-training
bash train.sh
```

`train.sh` installs `livekit-wakeword`, downloads data, generates synthetic
speech + adversarial negatives, trains, and exports `hey_jarvis.onnx`.

> Quick first run? Lower `steps` to `30000` and `n_samples` to `5000` in
> `hey_jarvis.yaml` to validate the pipeline end-to-end in minutes.
> Then bump to the production values (100000 / 25000) for the real model.

## Deliver models to the app

```bash
bash download_models.sh
```

This places all three `.onnx` files into:

```
jarvis/android/app/src/main/assets/wakeword/
├── melspectrogram.onnx
├── embedding_model.onnx
└── hey_jarvis.onnx
```

The app's `onnxruntime-android` dependency loads them from there at runtime.
(If you trained on Colab, just download `output/hey_jarvis/hey_jarvis.onnx`
and the two frozen models, then run `download_models.sh` locally or copy them
manually into the assets folder above.)

## Config knobs (`hey_jarvis.yaml`)

- `n_samples` / `steps` — dataset size + training steps (bigger = better)
- `custom_negative_phrases` — phrases that must **not** trigger (tuned for
  bilingual EN/UR mis-hears; add your own)
- `model.model_size` — `small` (mobile default) | `medium` | `large`
- `target_fp_per_hour` — false-positive budget (0.1 = strict 24/7 listening)
- `model_type` — `conv_attention` (default, best). Note: Android uses ONNX
  export, so conv_attention works; TFLite export would be `dnn`-only.

## Tuning sensitivity on-device

The Android side exposes `sensitivity` (0..1) in `WakeWordConfig`, mapped to
the detection threshold. After real-device testing, adjust it in
`voice/wakeword/WakeWordConfig.kt` or the Settings screen.
