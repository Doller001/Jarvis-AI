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
-keep class com.jarvis.assistant.ui.JarvisUiState { *; }

# 5. Picovoice Porcupine (JNI & Native Engine)
-keep class ai.picovoice.porcupine.** { *; }
-dontwarn ai.picovoice.porcupine.**

# 6. SQLite & Android Services
-keep class com.jarvis.assistant.memory.JarvisMemoryDatabase { *; }
-keep class com.jarvis.assistant.services.JarvisForegroundService { *; }
-keep class com.jarvis.assistant.accessibility.JarvisAccessibilityService { *; }

# 7. Strip verbose/debug logs in optimized release build
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
