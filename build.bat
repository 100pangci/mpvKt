@echo off
rem One-click build entry. Provisions JDK 21 + Android SDK + Gradle into
rem .android-env\ on first run (gitignored, nothing installed system-wide).
rem Foreign-platform leftovers (e.g. a .android-env copied from Linux) are
rem detected and replaced automatically.
setlocal EnableExtensions
cd /d "%~dp0"
set "ENV_DIR=%CD%\.android-env"
set "JAVA_HOME=%ENV_DIR%\jdk21"
set "GRADLE_USER_HOME=%ENV_DIR%\gradle-home"
set "SDK_DIR=%ENV_DIR%\android-sdk"

where curl.exe >nul 2>nul
if errorlevel 1 (
  echo [mpvKt] curl.exe is required, it is built into Windows 10 1803 and later.
  exit /b 1
)

if not exist "%JAVA_HOME%\bin\java.exe" (
  echo [mpvKt] Provisioning JDK 21...
  if exist "%JAVA_HOME%" rmdir /s /q "%JAVA_HOME%"
  if not exist "%ENV_DIR%" mkdir "%ENV_DIR%"
  curl -L --retry 3 -o "%ENV_DIR%\jdk21.zip" "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse" || goto :fail
  tar -xf "%ENV_DIR%\jdk21.zip" -C "%ENV_DIR%" || goto :fail
  for /d %%D in ("%ENV_DIR%\jdk-21*") do ren "%%D" "jdk21"
  if not exist "%JAVA_HOME%\bin\java.exe" goto :fail
  del "%ENV_DIR%\jdk21.zip" 2>nul
)

if not exist "%SDK_DIR%\cmdline-tools\latest\bin\sdkmanager.bat" (
  echo [mpvKt] Provisioning Android cmdline-tools...
  if exist "%SDK_DIR%\cmdline-tools\latest" rmdir /s /q "%SDK_DIR%\cmdline-tools\latest"
  mkdir "%SDK_DIR%\cmdline-tools" 2>nul
  curl -L --retry 3 -o "%ENV_DIR%\cmdtools.zip" "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip" || goto :fail
  tar -xf "%ENV_DIR%\cmdtools.zip" -C "%SDK_DIR%\cmdline-tools" || goto :fail
  ren "%SDK_DIR%\cmdline-tools\cmdline-tools" "latest"
  if not exist "%SDK_DIR%\cmdline-tools\latest\bin\sdkmanager.bat" goto :fail
  del "%ENV_DIR%\cmdtools.zip" 2>nul
)

if not exist "%SDK_DIR%\build-tools\36.0.0\aapt2.exe" (
  echo [mpvKt] Installing Android SDK packages, about 500 MB...
  if exist "%SDK_DIR%\build-tools\36.0.0" rmdir /s /q "%SDK_DIR%\build-tools\36.0.0"
  if exist "%SDK_DIR%\platform-tools" rmdir /s /q "%SDK_DIR%\platform-tools"
  (for /l %%i in (1,1,20) do @echo y) | "%SDK_DIR%\cmdline-tools\latest\bin\sdkmanager.bat" --licenses >nul
  call "%SDK_DIR%\cmdline-tools\latest\bin\sdkmanager.bat" "platforms;android-36" "build-tools;36.0.0" "platform-tools" >nul
  if not exist "%SDK_DIR%\build-tools\36.0.0\aapt2.exe" goto :fail
)

if not exist "%GRADLE_USER_HOME%\wrapper\dists\gradle-8.14.2-bin\2pb3mgt1p815evrl3weanttgr\gradle-8.14.2-bin.zip" (
  echo [mpvKt] Fetching Gradle distribution...
  mkdir "%GRADLE_USER_HOME%\wrapper\dists\gradle-8.14.2-bin\2pb3mgt1p815evrl3weanttgr"
  curl -L --retry 3 -o "%GRADLE_USER_HOME%\wrapper\dists\gradle-8.14.2-bin\2pb3mgt1p815evrl3weanttgr\gradle-8.14.2-bin.zip" "https://mirrors.cloud.tencent.com/gradle/gradle-8.14.2-bin.zip" || curl -L --retry 3 -o "%GRADLE_USER_HOME%\wrapper\dists\gradle-8.14.2-bin\2pb3mgt1p815evrl3weanttgr\gradle-8.14.2-bin.zip" "https://services.gradle.org/distributions/gradle-8.14.2-bin.zip"
  if not exist "%GRADLE_USER_HOME%\wrapper\dists\gradle-8.14.2-bin\2pb3mgt1p815evrl3weanttgr\gradle-8.14.2-bin.zip" goto :fail
)

> local.properties echo sdk.dir=%SDK_DIR:\=/%

call "%CD%\gradlew.bat" %*
exit /b %errorlevel%

:fail
echo [mpvKt] Provisioning failed, check your network connection and retry.
exit /b 1
