# ==============================================================================
# JARVIS Android App — Production R8 / ProGuard Optimization Rules
# ==============================================================================

# 1. Jetpack Compose Rules
-keepclassmembers class * extends androidx.compose.runtime.State { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
    @androidx.compose.runtime.ReadOnlyComposable *;
}
-dontwarn androidx.compose.**

# 2. Coroutines & Flow
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler { *; }
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}

# 3. OkHttp & Gson
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class com.google.gson.** { *; }
-keepclassmembers enum * { *; }

# 4. Data Models & JSON Schemas
-keep class com.jarvis.assistant.network.ProtocolModels** { *; }
-keep class com.jarvis.assistant.network.PingResult { *; }
-keep class com.jarvis.assistant.memory.CagResult { *; }
-keep class com.jarvis.assistant.memory.RagChunk { *; }
-keep class com.jarvis.assistant.memory.MemoryFact { *; }
-keep class com.jarvis.assistant.memory.MessageLog { *; }
-keep class com.jarvis.assistant.device.AppInfo { *; }
-keep class com.jarvis.assistant.brain.JarvisIntent** { *; }
-keep class com.jarvis.assistant.actionengine.** { *; }
-keep class com.jarvis.assistant.ui.JarvisUiState { *; }

# 5. SQLite & Android Services & Voice Pipeline
-keep class com.jarvis.assistant.memory.JarvisMemoryDatabase { *; }
-keep class com.jarvis.assistant.services.JarvisForegroundService { *; }
-keep class com.jarvis.assistant.services.JarvisQuickTileService { *; }
-keep class com.jarvis.assistant.services.JarvisNotificationListenerService { *; }
-keep class com.jarvis.assistant.services.BootRecoveryReceiver { *; }
-keep class com.jarvis.assistant.accessibility.JarvisAccessibilityService { *; }
-keep class com.jarvis.assistant.voice.** { *; }



# 6. ONNX Runtime Mobile (offline wake-word detector)
-keep class ai.onnxruntime.** { *; }
-keep interface ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**
# The bundled .onnx model assets are packaged into the APK by default
# (assets are never stripped by R8), so no extra -keepresources rule is needed.
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# 8. R8 Access Modifications & Optimizations
-allowaccessmodification
-repackageclasses 'com.jarvis.assistant.optimized'

