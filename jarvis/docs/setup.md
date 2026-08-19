# Jarvis AI — Setup, Environment Prerequisites & Build Guide

This guide details the complete system environment setup required to build the Jarvis Android APK, run Android unit tests, and configure the local environment.

---

## 🛠️ 1. Environment Prerequisites & Setup Instructions

To build the Android application (`./gradlew assembleDebug`) and run unit tests (`./gradlew test`), your development machine must have **Java 17 JDK**, **Gradle**, and the **Android SDK (API 34)** installed.

### 📋 Prerequisites Summary

1. **Java JDK 17** (Required by `compileOptions.sourceCompatibility = JavaVersion.VERSION_17` in `build.gradle.kts`)
2. **Gradle** (or standard Gradle Wrapper)
3. **Android SDK Platform API 34** & **Build Tools 34.0.0**
4. **`ANDROID_HOME` Environment Variable** configured

---

## 🐧 2. Linux Setup Instructions (Ubuntu / Debian / Mint)

### Step 1: Install OpenJDK 17

```bash
sudo apt-get update
sudo apt-get install openjdk-17-jdk -y
```

Verify Java installation:
```bash
java -version
```

Export `JAVA_HOME` in `~/.bashrc` or `~/.zshrc`:
```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
```

### Step 2: Install Gradle

```bash
sudo apt-get install gradle -y
```

Verify Gradle installation:
```bash
gradle -v
```

### Step 3: Install & Configure Android SDK (API 34)

1. Download Android Command Line Tools or Android Studio.
2. Set up SDK directory:
   ```bash
   mkdir -p $HOME/Android/Sdk
   export ANDROID_HOME=$HOME/Android/Sdk
   export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools
   ```
3. Add `ANDROID_HOME` to `~/.bashrc`:
   ```bash
   echo 'export ANDROID_HOME=$HOME/Android/Sdk' >> ~/.bashrc
   echo 'export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools' >> ~/.bashrc
   source ~/.bashrc
   ```
4. Accept licenses & install Android API 34 platforms:
   ```bash
   sdkmanager --licenses
   sdkmanager "platforms;android-34" "build-tools;34.0.0"
   ```

---

## 🍎 3. macOS Setup Instructions

```bash
# 1. Install Java 17 and Gradle via Homebrew
brew install openjdk@17 gradle

# 2. Configure JAVA_HOME in ~/.zshrc
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH=$JAVA_HOME/bin:$PATH

# 3. Configure ANDROID_HOME in ~/.zshrc
export ANDROID_HOME=$HOME/Library/Android/sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools
```

---

## 🪟 4. Windows Setup Instructions

1. **Install OpenJDK 17**: Download and install Eclipse Temurin 17 or OpenJDK 17.
2. **Install Gradle**: Install Gradle via `winget install Gradle.Gradle` or download zip.
3. **Configure Environment Variables**:
   - Set `JAVA_HOME` = `C:\Program Files\Eclipse Adoptium\jdk-17.x.x`
   - Set `ANDROID_HOME` = `%LOCALAPPDATA%\Android\Sdk`
   - Add `%JAVA_HOME%\bin` and `%ANDROID_HOME%\platform-tools` to system `PATH`.

---

## 📱 5. Building the Android Application

Once prerequisites are installed:

```bash
cd jarvis/android

# Run Android unit test suite
./gradlew test

# Assemble Debug APK
./gradlew assembleDebug
```

- Output Debug APK: `jarvis/android/app/build/outputs/apk/debug/app-debug.apk`
- Output Test Report: `jarvis/android/app/build/reports/tests/testDebugUnitTest/index.html`

---

## 🔐 6. App Permission Setup Flow

When launched on device, Jarvis guides users through:
1. **Microphone Permission**: For local wake-word detection.
2. **Notification Permission**: Persistent status notification for `JarvisForegroundService`.
3. **Accessibility Service**: Screen element automation via `JarvisAccessibilityService`.
4. **Battery Optimization Guidance**: Prevents system OEM battery killers from stopping background runtime.
