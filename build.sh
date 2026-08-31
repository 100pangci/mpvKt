#!/usr/bin/env bash
# One-click build entry. Provisions JDK 21 + Android SDK + Gradle into
# .android-env/ on first run (gitignored, nothing installed system-wide).
set -e
cd "$(dirname "$0")"
ENV_DIR="$PWD/.android-env"
export JAVA_HOME="$ENV_DIR/jdk21"
export GRADLE_USER_HOME="$ENV_DIR/gradle-home"
SDK_DIR="$ENV_DIR/android-sdk"

if [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo "[mpvKt] Provisioning JDK 21..."
  rm -rf "$JAVA_HOME"
  mkdir -p "$ENV_DIR"
  curl -L --retry 3 -o "$ENV_DIR/jdk21.tar.gz" \
    "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"
  tar -xzf "$ENV_DIR/jdk21.tar.gz" -C "$ENV_DIR"
  mv "$ENV_DIR"/jdk-21* "$ENV_DIR/jdk21"
  rm -f "$ENV_DIR/jdk21.tar.gz"
fi

if [ ! -f "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "[mpvKt] Provisioning Android cmdline-tools..."
  rm -rf "$SDK_DIR/cmdline-tools/latest"
  mkdir -p "$SDK_DIR/cmdline-tools"
  curl -L --retry 3 -o "$ENV_DIR/cmdtools.zip" \
    "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  unzip -q "$ENV_DIR/cmdtools.zip" -d "$SDK_DIR/cmdline-tools"
  mv "$SDK_DIR/cmdline-tools/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
  rm -f "$ENV_DIR/cmdtools.zip"
fi

if [ ! -x "$SDK_DIR/build-tools/36.0.0/aapt2" ]; then
  echo "[mpvKt] Installing Android SDK packages (~500 MB)..."
  rm -rf "$SDK_DIR/build-tools/36.0.0" "$SDK_DIR/platform-tools"
  yes | "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null
  "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" \
    "platforms;android-36" "build-tools;36.0.0" "platform-tools" > /dev/null
fi

if [ ! -f "$GRADLE_USER_HOME/wrapper/dists/gradle-8.14.2-bin/2pb3mgt1p815evrl3weanttgr/gradle-8.14.2-bin.zip" ]; then
  echo "[mpvKt] Fetching Gradle distribution..."
  DIST_DIR="$GRADLE_USER_HOME/wrapper/dists/gradle-8.14.2-bin/2pb3mgt1p815evrl3weanttgr"
  mkdir -p "$DIST_DIR"
  curl -L --retry 3 -o "$DIST_DIR/gradle-8.14.2-bin.zip" \
    "https://mirrors.cloud.tencent.com/gradle/gradle-8.14.2-bin.zip" ||
    curl -L --retry 3 -o "$DIST_DIR/gradle-8.14.2-bin.zip" \
      "https://services.gradle.org/distributions/gradle-8.14.2-bin.zip"
fi

printf 'sdk.dir=%s\n' "$SDK_DIR" > local.properties

exec ./gradlew "$@"
