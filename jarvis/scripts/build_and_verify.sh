#!/usr/bin/env bash
# Build & verify the rebuilt Jarvis Android UI.
# Installs JDK17 + Gradle + Android SDK, then runs a REAL assembleDebug + unit tests.
# Usage:  bash scripts/build_and_verify.sh
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BASE="${PROJECT_DIR}/.build-env"
JAVA_HOME="$BASE/jdk"
GRADLE_HOME="$BASE/gradle"
ANDROID_HOME="$BASE/android-sdk"
ANDROID_DIR="$PROJECT_DIR/android"

echo "==> Project: $PROJECT_DIR"

# ---- 1. JDK 17 ----
if [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo "==> Installing Temurin JDK 17..."
  mkdir -p "$JAVA_HOME"
  curl -fsSL -o /tmp/jdk.tar.gz \
    "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse?project=jdk"
  tar -xzf /tmp/jdk.tar.gz -C "$JAVA_HOME" --strip-components=1
fi
GRADLE_USER_HOME="$BASE/.gradle"
ANDROID_USER_HOME="$BASE/.android"
export JAVA_HOME ANDROID_HOME GRADLE_HOME GRADLE_USER_HOME ANDROID_USER_HOME
export PATH="$JAVA_HOME/bin:$PATH"
java -version

# ---- 2. Gradle 8.5 ----
if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  echo "==> Installing Gradle 8.5..."
  mkdir -p "$GRADLE_HOME"
  curl -fsSL -o /tmp/gradle.zip \
    "https://services.gradle.org/distributions/gradle-8.5-bin.zip"
  unzip -q /tmp/gradle.zip -d /tmp/gradle_unpack
  mv /tmp/gradle_unpack/gradle-8.5/* "$GRADLE_HOME/"
  rm -rf /tmp/gradle_unpack /tmp/gradle.zip
fi
export PATH="$GRADLE_HOME/bin:$PATH"
gradle -v

# ---- 3. Android SDK ----
if [ ! -d "$ANDROID_HOME/cmdline-tools/latest" ]; then
  echo "==> Installing Android cmdline-tools..."
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  curl -fsSL -o /tmp/cmdtools.zip \
    "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  unzip -q /tmp/cmdtools.zip -d "$ANDROID_HOME/cmdline-tools"
  mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm -f /tmp/cmdtools.zip
fi
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

if command -v sdkmanager >/dev/null 2>&1; then
  echo "==> Accepting SDK licenses & installing components..."
  (yes || true) | sdkmanager --sdk_root="$ANDROID_HOME" --licenses >/dev/null 2>&1 || true
  sdkmanager --sdk_root="$ANDROID_HOME" \
    "platform-tools" "platforms;android-34" "build-tools;34.0.0" >/dev/null 2>&1 || true
fi

echo "sdk.dir=${ANDROID_HOME}" > "$ANDROID_DIR/local.properties"

# ---- 4. Validate XML ----
echo "==> Validating XML resources..."
python3 "$PROJECT_DIR/scripts/validate_xml.py"

# ---- 5. Real Gradle build ----
echo "==> assembleDebug (real compile of all UI + permission code)..."
cd "$ANDROID_DIR"
gradle assembleDebug --no-daemon || ./gradlew assembleDebug --no-daemon

echo "==> unit tests..."
gradle testDebugUnitTest --no-daemon || ./gradlew testDebugUnitTest --no-daemon

echo ""
echo "BUILD VERIFIED: assembleDebug + unit tests passed."
echo "APK: $ANDROID_DIR/app/build/outputs/apk/debug/app-debug.apk"
