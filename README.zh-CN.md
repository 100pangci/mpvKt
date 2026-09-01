# <img alt="应用图标" src=".github/assets/app_icon.svg" width="48" /> mpvKt

基于 [mpv](https://mpv.io) / [mpv-android](https://github.com/mpv-android/mpv-android) 的 Android 视频播放器，在原版之上提供更顺手的用户界面。

[English](README.md) | 简体中文

![许可证](https://img.shields.io/badge/license-MPL--2.0-blue)
![平台](https://img.shields.io/badge/platform-Android%205.0%2B-green)
![语言](https://img.shields.io/badge/localization-18%20%E7%A7%8D%E8%AF%AD%E8%A8%80-orange)
![欢迎 PR](https://img.shields.io/badge/PRs-welcome-brightgreen)

## 关于本分支

原版 [mpvKt](https://github.com/abdallahmehiz/mpvKt)（作者
[@abdallahmehiz](https://github.com/abdallahmehiz)）已归档。本仓库是持续维护的延续分支，升级了 mpv 内核并持续修复问题、添加功能，开发进度见 `dev` 分支。

本分支目前的亮点：

- **mpv-android-lib 0.1.12**——通过兼容层从 0.1.9 升级，用户侧体验无回退。
- **截图功能重做**——带字幕/不带字幕独立按钮，按住滑动逐帧取材，上滑取消，自定义保存目录并正确处理存储权限。
- **18 种语言**，应用内即可切换语言。
- 修复了大量上游 issue：字幕延迟被重置、打开不支持文件时崩溃、画中画 NPE、销毁后段错误等。

## 功能特性

**播放器**

- 由 mpv 驱动：硬件/软件解码、`gpu-next` 渲染器
- 精确 seek（可关闭），滑动快进时长与双击行为均可配置
- 视频滤镜：亮度、对比度、伽马、饱和度、色调
- 去色带（CPU/GPU）、YUV420P 像素格式选项
- 章节支持与当前章节指示器
- 倍速播放，支持单视频记忆与全局默认
- 画中画、后台播放、睡眠定时器
- 跨会话记忆播放位置

**手势**

- 横向滑动快进/快退，纵向滑动调节音量与亮度
- 双击快进或暂停，左/中/右三区可独立配置
- 按住即可倍速播放
- 手势可通过 `input.conf` 完全自定义

**字幕**

- 自动加载同名外挂字幕
- 首选字幕/音轨语言（ISO 代码）
- 主字幕 + 次字幕，延迟独立可调
- 完整排版控制：字体、字号、颜色、描边样式、阴影、缩放、位置、ASS/SSA 覆盖
- 逐轨延迟校准（"听到声音/看到文字"），支持设为默认

**截图**

- 一键截取：带字幕或不带字幕
- 按住截图按钮左右滑动逐帧微调，松手截取精确帧
- 上滑取消，屏幕有"松手后取消截图"提示
- 自定义保存目录（默认 `Pictures/mpvKt`）

**自定义与进阶**

- 应用内直接编辑 `mpv.conf` 与 `input.conf`
- 自定义按钮可执行任意 Lua 代码
- 详细日志与日志导出，方便反馈问题

## 预览

<img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/1_en-US.png" width="24%" /> <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/2_en-US.png" width="24%" /> <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/3_en-US.png" width="24%"> <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/4_en-US.png" width="24%" />
<img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/5_en-US.png" width="49%" /> <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/6_en-US.png" width="49%" />
<img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/7_en-US.png" width="49%" /> <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/8_en-US.png" width="49%" />

## 安装

本分支暂未发布预编译 APK——先从源码构建吧（只需一条命令，见下文）。
日常使用拷贝 `app-arm64-v8a-debug.apk` 到手机安装即可；`universal` 包覆盖全部架构。

## 构建

构建工具链（JDK 21、Android SDK、Gradle）会在首次运行时自动装配到 gitignore
的 `.android-env/` 目录——不污染系统全局环境，也无需安装 Android Studio。

Linux：

```sh
./build.sh assembleDebug
```

Windows：

```bat
build.bat assembleDebug
```

后续运行会跳过装配直接构建。APK 输出在 `app/build/outputs/apk/`（分架构与
universal 多种变体）。Debug 包使用调试签名；Release 包需要通过环境变量提供
签名密钥信息（见 CI 工作流）。

## 参与贡献

欢迎在 [100pangci/mpvKt](https://github.com/100pangci/mpvKt) 提交 issue 和
PR。请以 `dev` 分支为目标，提交前先跑一遍
`./build.sh detekt assembleDebug`（Windows 用对应的 `.bat`）。

## 致谢

- [abdallahmehiz](https://github.com/abdallahmehiz)——mpvKt 原作者
- [mpv-android](https://github.com/mpv-android/mpv-android)——基础 mpv 库
- [K1rakishou/Fuck-Storage-Access-Framework](https://github.com/K1rakishou/Fuck-Storage-Access-Framework)
  与 [zhanghai/MaterialPreference](https://github.com/zhanghai/MaterialPreference)
- 所有上游贡献者与翻译者

### 关于 `mpv-android-lib`

本项目使用的 mpv 绑定库是
[`io.github.abdallahmehiz:mpv-android-lib`](https://github.com/abdallahmehiz/mpv-android)
——[mpv-android](https://github.com/mpv-android/mpv-android) 的库化分支，提供
实例化 `MPV` API、`mpv_node` 绑定、多实例与 DASH 支持，由 mpvKt 原作者发布到
Maven Central。本分支的播放器层（`MPVLib` 兼容单例与 `MPVView`）即基于该
fork 的 API 构建。

该库的上游仓库已归档，但 Maven Central 构件是不可变的，依赖解析不受影响。
若日后需要修改该库（例如升级 libmpv），计划将其 fork 到本组织下，通过其
`buildscripts` 重新构建 AAR 并以新的 group id 发布——其 MIT 许可允许这样做。

## 许可证

本项目基于 [Mozilla Public License 2.0](LICENSE) 开源。
它是 [mpvKt](https://github.com/abdallahmehiz/mpvKt) 的延续分支，原代码采用
Apache License 2.0 许可。
