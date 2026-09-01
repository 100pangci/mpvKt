# <img alt="app icon" src=".github/assets/app_icon.svg" width="48" /> mpvKt

A media player for Android based on [mpv](https://mpv.io) / [mpv-android](https://github.com/mpv-android/mpv-android), aiming to provide a *nicer* user interface over the original.

English | [简体中文](README.zh-CN.md)

![License](https://img.shields.io/badge/license-MPL--2.0-blue)
![Platform](https://img.shields.io/badge/platform-Android%206.0%2B-green)
![Languages](https://img.shields.io/badge/localization-18%20languages-orange)
![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen)

## About this fork

The original [mpvKt](https://github.com/abdallahmehiz/mpvKt) by
[@abdallahmehiz](https://github.com/abdallahmehiz) has been archived. This is a
maintained continuation with an upgraded mpv core and a steady stream of fixes
and features, tracked on the `dev` branch.

Highlights of this fork so far:

- **mpv-android-lib 0.1.12** — upgraded from 0.1.9 through a compatibility
  layer, no user-facing regression.
- **Reworked screenshots** — dedicated with/without-subtitles buttons,
  frame-stepping capture, swipe-up-to-cancel, custom save directory with
  proper storage permissions.
- **18 languages** with an in-app language switcher.
- Dozens of upstream issue fixes: subtitle delay resets, crashes on opening
  unsupported files, PiP NPEs, teardown segfaults, and more.

## Features

**Player**

- Powered by mpv: hardware & software decoding, `gpu-next` renderer
- Precise seeking, configurable seek duration and double-tap actions
- Video filters: brightness, contrast, gamma, saturation, hue
- Debanding (CPU/GPU), YUV420P pixel format option
- Chapters with a current-chapter indicator
- Playback speed with per-video memory and defaults
- Picture-in-Picture, background playback, sleep timer
- Resume playback position across sessions

**Gestures**

- Horizontal slide to seek, vertical slides for volume and brightness
- Double-tap to seek or play/pause, configurable per side (left/center/right)
- Hold to play at multiple speeds
- Full gesture customization via `input.conf`

**Subtitles**

- Automatic loading of external subtitles with matching names
- Preferred subtitle/audio languages (ISO codes)
- Primary + secondary subtitles with independent delays
- Full typography control: font, size, colors, border style, shadow, scale,
  position, ASS/SSA override
- Per-track delay calibration ("voice heard / text seen") with set-as-default

**Screenshots**

- One-tap capture with or without subtitles
- Hold a screenshot button and slide left/right to scrub frames, release to
  capture the exact frame
- Swipe up to cancel with an on-screen hint
- Custom save directory (or the default `Pictures/mpvKt`)

**Customization & power-user**

- Edit `mpv.conf` and `input.conf` in-app
- Custom buttons that execute arbitrary Lua code
- Verbose logging and log export for bug reports

## Showcase

<img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/1_en-US.png" width="24%" /> <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/2_en-US.png" width="24%" /> <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/3_en-US.png" width="24%"> <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/4_en-US.png" width="24%" />
<img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/5_en-US.png" width="49%" /> <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/6_en-US.png" width="49%" />
<img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/7_en-US.png" width="49%" /> <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/8_en-US.png" width="49%" />

## Installation

Prebuilt APKs are not yet published for this fork — build from source for now
(it only takes one command, see below). Copy `app-arm64-v8a-debug.apk` to your
device and install it for everyday use; the `universal` APK covers all
architectures.

## Building

The build toolchain (JDK 21, Android SDK, Gradle) is provisioned automatically
into the gitignored `.android-env/` directory on first run — nothing is
installed system-wide and no Android Studio is required.

Linux:

```sh
./build.sh assembleDebug
```

Windows:

```bat
build.bat assembleDebug
```

Subsequent runs skip provisioning and build directly. APKs land in
`app/build/outputs/apk/` (per-ABI and universal variants). Debug builds are
signed with the debug key; release builds expect keystore credentials via
environment variables (see CI workflow).

## Contributing

Issues and pull requests are welcome at
[100pangci/mpvKt](https://github.com/100pangci/mpvKt). Please target the `dev`
branch and run `./build.sh detekt assembleDebug` (or the `.bat` equivalent)
before submitting.

## Acknowledgments

- [abdallahmehiz](https://github.com/abdallahmehiz) for creating mpvKt
- [mpv-android](https://github.com/mpv-android/mpv-android) for the base mpv
  library
- [K1rakishou/Fuck-Storage-Access-Framework](https://github.com/K1rakishou/Fuck-Storage-Access-Framework)
  and [zhanghai/MaterialPreference](https://github.com/zhanghai/MaterialPreference)
- All upstream contributors and translators

### About `mpv-android-lib`

The mpv binding this project builds on is
[`io.github.abdallahmehiz:mpv-android-lib`](https://github.com/abdallahmehiz/mpv-android)
— a library fork of [mpv-android](https://github.com/mpv-android/mpv-android)
with an instance-based `MPV` API, `mpv_node` bindings, multi-instance and
DASH support, published to Maven Central by the original mpvKt author. The
player layer of this fork (the `MPVLib` compatibility singleton and
`MPVView`) targets that fork's API.

The library's upstream repository has been archived, but Maven Central
artifacts are immutable and remain resolvable, so the dependency keeps
working as-is. If the library ever needs patching (e.g. a libmpv bump), the
plan is to fork it under this organization, rebuild the AAR through its
`buildscripts` and publish under a new group id — its MIT license allows
this.

## License

This project is licensed under the [Mozilla Public License 2.0](LICENSE).
It is a fork of [mpvKt](https://github.com/abdallahmehiz/mpvKt), whose
original code is licensed under the Apache License 2.0.
