# Wake-word model (Picovoice Porcupine)

Drop your custom **"Hey Jarvis"** keyword file here:

```
hey-jarvis_en_android_v3_0_0.ppn
```

## How to get it

1. Create a free account at https://console.picovoice.ai
2. Console → **Create Custom Wake Word** → train the phrase `Hey Jarvis`
3. Export for **Android** → download `hey-jarvis_en_android_v3_0_0.ppn`
4. Copy it into this folder (`app/src/main/assets/`)

## Access key

The detector is enabled only when an AccessKey is present. Set it in
`app/build.gradle.kts`:

```kotlin
buildConfigField("String", "JARVIS_PICOVOICE_ACCESS_KEY", "\"<your-key>\"")
```

## Until then

`PorcupineWakeWordDetector.isAvailable()` returns `false` and the app keeps
working in **fallback text-matching mode** (continuous STT + phrase match) —
nothing breaks, it just uses more battery.