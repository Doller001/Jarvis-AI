# Jarvis AI — Accessibility Automation

`JarvisAccessibilityService` provides high-level UI element interaction APIs:

```kotlin
fun tap(targetText: String): Boolean
fun tapById(viewId: String): Boolean
fun scroll(direction: String): Boolean
fun back(): Boolean
fun home(): Boolean
fun openRecents(): Boolean
fun typeText(text: String): Boolean
fun readScreen(): String
```

Password fields are automatically detected (`isPassword == true`) and masked in logs to protect user privacy.
