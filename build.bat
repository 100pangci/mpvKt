@echo off
rem One-click build entry. Reuses an already configured toolchain when one is
rem present: JAVA_HOME / GRADLE_USER_HOME / ANDROID_HOME (or
rem ANDROID_SDK_ROOT) environment variables, or the shared D:\SDK layout on
rem this machine. Otherwise it provisions JDK 21 + Android SDK (incl. NDK) +
rem Gradle into .android-env\ on first run (gitignored, nothing installed
rem system-wide). Foreign-platform leftovers (e.g. a .android-env copied from
rem Linux) are detected and replaced automatically.
setlocal EnableExtensions
cd /d "%~dp0"
set "ENV_DIR=%CD%\.android-env"

rem Resolve the toolchain: existing env vars, then D:\SDK, then .android-env.
if defined JAVA_HOME if not exist "%JAVA_HOME%\bin\java.exe" set "JAVA_HOME="
if defined GRADLE_USER_HOME if not exist "%GRADLE_USER_HOME%\wrapper" set "GRADLE_USER_HOME="
set "SDK_DIR="
if defined ANDROID_HOME set "SDK_DIR=%ANDROID_HOME%"
if not defined SDK_DIR if defined ANDROID_SDK_ROOT set "SDK_DIR=%ANDROID_SDK_ROOT%"
if defined SDK_DIR if not exist "%SDK_DIR%\platform-tools" set "SDK_DIR="
if not defined JAVA_HOME if exist "D:\SDK\Jdk\jdk21\bin\java.exe" set "JAVA_HOME=D:\SDK\Jdk\jdk21"
if not defined GRADLE_USER_HOME if exist "D:\SDK\GradleUserHome\wrapper" set "GRADLE_USER_HOME=D:\SDK\GradleUserHome"
if not defined SDK_DIR if exist "D:\SDK\AndroidSdk\platform-tools" set "SDK_DIR=D:\SDK\AndroidSdk"
if not defined JAVA_HOME set "JAVA_HOME=%ENV_DIR%\jdk21"
if not defined GRADLE_USER_HOME set "GRADLE_USER_HOME=%ENV_DIR%\gradle-home"
if not defined SDK_DIR set "SDK_DIR=%ENV_DIR%\android-sdk"

rem Figure out what the chosen toolchain is still missing.
set "NEED_JDK="
set "NEED_CMDTOOLS="
set "NEED_SDKPKGS="
set "NEED_NDK="
set "NEED_GRADLE="
if not exist "%JAVA_HOME%\bin\java.exe" set "NEED_JDK=1"
if not exist "%SDK_DIR%\cmdline-tools\latest\bin\sdkmanager.bat" if not exist "%SDK_DIR%\cmdline-tools\latest\bin\android.exe" set "NEED_CMDTOOLS=1"
if not exist "%SDK_DIR%\platforms\android-36\android.jar" set "NEED_SDKPKGS=1"
if not exist "%SDK_DIR%\build-tools\36.0.0\aapt2.exe" set "NEED_SDKPKGS=1"
if not exist "%SDK_DIR%\platform-tools\adb.exe" set "NEED_SDKPKGS=1"
if not exist "%SDK_DIR%\ndk\28.0.13004108\toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-strip.exe" set "NEED_NDK=1"
if not exist "%GRADLE_USER_HOME%\wrapper\dists\gradle-8.14.2-bin\2pb3mgt1p815evrl3weanttgr\gradle-8.14.2-bin.zip" set "NEED_GRADLE=1"
if not defined NEED_JDK if not defined NEED_CMDTOOLS if not defined NEED_SDKPKGS if not defined NEED_NDK if not defined NEED_GRADLE goto :build

where curl.exe >nul 2>nul
if errorlevel 1 (
  echo [mpvKt] curl.exe is required for provisioning, it is built into Windows 10 1803 and later.
  exit /b 1
)

if defined NEED_JDK (
  echo [mpvKt] Provisioning JDK 21...
  if exist "%JAVA_HOME%" rmdir /s /q "%JAVA_HOME%"
  if not exist "%ENV_DIR%" mkdir "%ENV_DIR%"
  curl -L --retry 3 -o "%ENV_DIR%\jdk21.zip" "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse" || goto :fail
  tar -xf "%ENV_DIR%\jdk21.zip" -C "%ENV_DIR%" || goto :fail
  for /d %%D in ("%ENV_DIR%\jdk-21*") do ren "%%D" "jdk21"
  if not exist "%JAVA_HOME%\bin\java.exe" goto :fail
  del "%ENV_DIR%\jdk21.zip" 2>nul
)

if defined NEED_CMDTOOLS (
  echo [mpvKt] Provisioning Android cmdline-tools...
  if exist "%SDK_DIR%\cmdline-tools\latest" rmdir /s /q "%SDK_DIR%\cmdline-tools\latest"
  mkdir "%SDK_DIR%\cmdline-tools" 2>nul
  curl -L --retry 3 -o "%ENV_DIR%\cmdtools.zip" "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip" || goto :fail
  tar -xf "%ENV_DIR%\cmdtools.zip" -C "%SDK_DIR%\cmdline-tools" || goto :fail
  ren "%SDK_DIR%\cmdline-tools\cmdline-tools" "latest"
  if not exist "%SDK_DIR%\cmdline-tools\latest\bin\sdkmanager.bat" goto :fail
  del "%ENV_DIR%\cmdtools.zip" 2>nul
)

if defined NEED_SDKPKGS set "NEED_SDKMETA=1"
if defined NEED_NDK set "NEED_SDKMETA=1"
if defined NEED_SDKMETA if not exist "%SDK_DIR%\cmdline-tools\latest\bin\android.exe" (
  (for /l %%i in (1,1,20) do @echo y) | "%SDK_DIR%\cmdline-tools\latest\bin\sdkmanager.bat" --licenses >nul
)

if defined NEED_SDKPKGS (
  echo [mpvKt] Installing Android SDK packages, about 500 MB...
  if exist "%SDK_DIR%\build-tools\36.0.0" rmdir /s /q "%SDK_DIR%\build-tools\36.0.0"
  if exist "%SDK_DIR%\platform-tools" rmdir /s /q "%SDK_DIR%\platform-tools"
  call :install "platforms/android-36" "platforms;android-36" || goto :fail
  call :install "build-tools/36.0.0" "build-tools;36.0.0" || goto :fail
  call :install "platform-tools" "platform-tools" || goto :fail
  if not exist "%SDK_DIR%\build-tools\36.0.0\aapt2.exe" goto :fail
)

if defined NEED_NDK (
  echo [mpvKt] Installing NDK r28, about 2 GB, strips the prebuilt native libraries...
  call :install "ndk/28.0.13004108" "ndk;28.0.13004108" || goto :fail
  if not exist "%SDK_DIR%\ndk\28.0.13004108\toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-strip.exe" goto :fail
)

if defined NEED_GRADLE (
  echo [mpvKt] Fetching Gradle distribution...
  mkdir "%GRADLE_USER_HOME%\wrapper\dists\gradle-8.14.2-bin\2pb3mgt1p815evrl3weanttgr" 2>nul
  curl -L --retry 3 -o "%GRADLE_USER_HOME%\wrapper\dists\gradle-8.14.2-bin\2pb3mgt1p815evrl3weanttgr\gradle-8.14.2-bin.zip" "https://mirrors.cloud.tencent.com/gradle/gradle-8.14.2-bin.zip" || curl -L --retry 3 -o "%GRADLE_USER_HOME%\wrapper\dists\gradle-8.14.2-bin\2pb3mgt1p815evrl3weanttgr\gradle-8.14.2-bin.zip" "https://services.gradle.org/distributions/gradle-8.14.2-bin.zip"
  if not exist "%GRADLE_USER_HOME%\wrapper\dists\gradle-8.14.2-bin\2pb3mgt1p815evrl3weanttgr\gradle-8.14.2-bin.zip" goto :fail
)

:build
> local.properties echo sdk.dir=%SDK_DIR:\=/%
call "%CD%\gradlew.bat" %*
exit /b %errorlevel%

:install
if exist "%SDK_DIR%\cmdline-tools\latest\bin\android.exe" (
  "%SDK_DIR%\cmdline-tools\latest\bin\android.exe" --sdk="%SDK_DIR%" sdk install %1
) else (
  call "%SDK_DIR%\cmdline-tools\latest\bin\sdkmanager.bat" %2 >nul
)
if errorlevel 1 exit /b 1
exit /b 0

:fail
echo [mpvKt] Provisioning failed, check your network connection and retry.
exit /b 1
