#!/usr/bin/env bash
# Install JDK17 + Android SDK (cmdline-tools, platform-34, build-tools 34.0.0)
# into a home dir (no root needed). Idempotent-ish.
set -e
export BASE="$HOME/android-build-env"
mkdir -p "$BASE"
export JAVA_HOME="$BASE/jdk"
export ANDROID_HOME="$BASE/android-sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

echo "[1/6] Downloading Temurin JDK 17..."
if [ ! -d "$JAVA_HOME" ]; then
  cd "$BASE"
  curl -fsSL -o jdk.tar.gz "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse?project=jdk" \
    || curl -fsSL -o jdk.tar.gz "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.11%2B9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.11_9.tar.gz"
  mkdir -p "$JAVA_HOME"
  tar -xzf jdk.tar.gz -C "$JAVA_HOME" --strip-components=1
fi
java -version
echo "JAVA OK"

echo "[2/6] Android cmdline-tools..."
if [ ! -d "$ANDROID_HOME/cmdline-tools" ]; then
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  cd "$BASE"
  curl -fsSL -o cmdtools.zip "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  unzip -q cmdtools.zip -d "$ANDROID_HOME/cmdline-tools"
  mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
fi

echo "[3/6] Accept licenses + install packages..."
yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses >/dev/null
sdkmanager --sdk_root="$ANDROID_HOME" \
  "platform-tools" \
  "platforms;android-34" \
  "build-tools;34.0.0"

echo "[4/6] Create local.properties for the project..."
PROJECT="$BASE/../../Downloads/raphael-ai-assistant-main/jarvis/android"
PROJECT="/home/saif/Downloads/raphael-ai-assistant-main/jarvis/android"
echo "sdk.dir=${ANDROID_HOME}" > "$PROJECT/local.properties"
echo "android.home=${ANDROID_HOME}" >> "$PROJECT/local.properties"

echo "[5/6] Done. Env ready."
echo "JAVA_HOME=$JAVA_HOME"
echo "ANDROID_HOME=$ANDROID_HOME"
echo "BUILD_ENV_READY=1" > "$BASE/.env_ready"
