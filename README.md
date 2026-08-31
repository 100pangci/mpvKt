# <img alt="app icon" src=".github/assets/app_icon.svg" width="48" /> mpvKt
A media player for Android based on [mpv-android](https://github.com/mpv-android/mpv-android) aiming to provide a *nicer* user interface over the original.

## Additional features
- Nicer player UI
- Better playback history implementation
- Easier customization
- Sleep timer, Speed presets
- Smoother PiP
- Frame-step screenshots: hold a screenshot button and slide left/right to scrub frames, release to capture with or without subtitles
- Customizable screenshot directory

## Showcase

<img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/1_en-US.png" width="24%" /> <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/2_en-US.png" width="24%" /> <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/3_en-US.png" width="24%"> <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/4_en-US.png" width="24%" />
<img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/5_en-US.png" width="49%" /> <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/6_en-US.png" width="49%" />
<img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/7_en-US.png" width="49%" /> <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/8_en-US.png" width="49%" />

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
`app/build/outputs/apk/`.

## Acknowledgments
- [mpv-android](https://github.com/mpv-android) for the base mpv library to use for this project.

## License

This project is licensed under the [Mozilla Public License 2.0](LICENSE).
It is a fork of [mpvKt](https://github.com/abdallahmehiz/mpvKt), whose
original code is licensed under the Apache License 2.0.
