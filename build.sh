#!/usr/bin/env bash
# One-click build entry. Reuses an already configured toolchain when one is
# present (JAVA_HOME / GRADLE_USER_HOME / ANDROID_HOME or ANDROID_SDK_ROOT
# environment variables); otherwise provisions JDK 21 + Android SDK (incl.
# NDK) + Gradle into .android-env/ on first run (gitignored, nothing
# installed system-wide).
set -e
cd "$(dirname "$0")"
ENV_DIR="$PWD/.android-env"

# ---- Resolve the toolchain: existing env vars, then .android-env ----
if [ -n "$JAVA_HOME" ] && [ ! -x "$JAVA_HOME/bin/java" ]; then
  unset JAVA_HOME
fi
if [ -n "$GRADLE_USER_HOME" ] && [ ! -d "$GRADLE_USER_HOME" ]; then
  unset GRADLE_USER_HOME
fi
if [ -z "$ANDROID_HOME" ]; then
  ANDROID_HOME="${ANDROID_SDK_ROOT:-}"
fi
if [ -n "$ANDROID_HOME" ] && [ ! -d "$ANDROID_HOME/platform-tools" ]; then
  ANDROID_HOME=""
fi
SDK_DIR="${ANDROID_HOME:-$ENV_DIR/android-sdk}"
JAVA_HOME="${JAVA_HOME:-$ENV_DIR/jdk21}"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ENV_DIR/gradle-home}"
export JAVA_HOME GRADLE_USER_HOME

install_pkg() { # $1: current package id, $2: legacy sdkmanager id
  if [ -x "$SDK_DIR/cmdline-tools/latest/bin/android" ]; then
    "$SDK_DIR/cmdline-tools/latest/bin/android" --sdk="$SDK_DIR" sdk install "$1"
  else
    "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" "$2" > /dev/null
  fi
}

# ---- Figure out what the chosen toolchain is still missing ----
NEED_JDK=0
if [ ! -x "$JAVA_HOME/bin/java" ]; then NEED_JDK=1; fi
NEED_CMDTOOLS=0
if [ ! -f "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ] && [ ! -x "$SDK_DIR/cmdline-tools/latest/bin/android" ]; then NEED_CMDTOOLS=1; fi
NEED_SDKPKGS=0
if [ ! -f "$SDK_DIR/platforms/android-36/android.jar" ] || [ ! -x "$SDK_DIR/build-tools/36.0.0/aapt2" ] || [ ! -x "$SDK_DIR/platform-tools/adb" ]; then NEED_SDKPKGS=1; fi
NEED_NDK=0
if [ ! -x "$SDK_DIR/ndk/28.0.13004108/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" ]; then NEED_NDK=1; fi
NEED_GRADLE=0
if [ ! -f "$GRADLE_USER_HOME/wrapper/dists/gradle-8.14.2-bin/2pb3mgt1p815evrl3weanttgr/gradle-8.14.2-bin.zip" ]; then NEED_GRADLE=1; fi

if [ "$NEED_JDK" = 1 ]; then
  echo "[mpvKt] Provisioning JDK 21..."
  rm -rf "$JAVA_HOME"
  mkdir -p "$ENV_DIR"
  curl -L --retry 3 -o "$ENV_DIR/jdk21.tar.gz" \
    "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"
  tar -xzf "$ENV_DIR/jdk21.tar.gz" -C "$ENV_DIR"
  mv "$ENV_DIR"/jdk-21* "$ENV_DIR/jdk21"
  rm -f "$ENV_DIR/jdk21.tar.gz"
fi

if [ "$NEED_CMDTOOLS" = 1 ]; then
  echo "[mpvKt] Provisioning Android cmdline-tools..."
  rm -rf "$SDK_DIR/cmdline-tools/latest"
  mkdir -p "$SDK_DIR/cmdline-tools"
  curl -L --retry 3 -o "$ENV_DIR/cmdtools.zip" \
    "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  unzip -q "$ENV_DIR/cmdtools.zip" -d "$SDK_DIR/cmdline-tools"
  mv "$SDK_DIR/cmdline-tools/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
  rm -f "$ENV_DIR/cmdtools.zip"
fi

if [ "$NEED_SDKPKGS" = 1 ] || [ "$NEED_NDK" = 1 ]; then
  if [ ! -x "$SDK_DIR/cmdline-tools/latest/bin/android" ]; then
    yes | "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null
  fi
fi

if [ "$NEED_SDKPKGS" = 1 ]; then
  echo "[mpvKt] Installing Android SDK packages (~500 MB)..."
  rm -rf "$SDK_DIR/build-tools/36.0.0" "$SDK_DIR/platform-tools"
  install_pkg "platforms/android-36" "platforms;android-36"
  install_pkg "build-tools/36.0.0" "build-tools;36.0.0"
  install_pkg "platform-tools" "platform-tools"
fi

if [ "$NEED_NDK" = 1 ]; then
  echo "[mpvKt] Installing NDK r28 (~2 GB, strips the prebuilt native libraries)..."
  install_pkg "ndk/28.0.13004108" "ndk;28.0.13004108"
fi

if [ "$NEED_GRADLE" = 1 ]; then
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
