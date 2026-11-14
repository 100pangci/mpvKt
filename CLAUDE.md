# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概况

mpvKt 是 Android 上的 mpv/libmpv 播放器前端。这里只有 `:app` 一个 Gradle 模块；Kotlin 包名为 `live.mehiz.mpvkt`，applicationId 为 `live.ywpc05.mpvkt`。上游 mpv-android 的 Java/Kotlin 绑定来自 Maven 依赖 `io.github.100pangci:mpv-android-lib:0.1.13`（`gradle/libs.versions.toml`），其源码不在本仓库，类的包名是 `is.xyz.mpv`。

本仓库是原 mpvKt 的独立功能增强分支，README 要求 PR 以 `dev` 分支为目标。项目以 MPL-2.0 发布；原始代码为 Apache-2.0。

## 命令

日常使用包装脚本，而不是直接调用 `./gradlew`：`build.sh`（Linux）/`build.bat`（Windows）会在首次运行时把 JDK 21、Android SDK 36/build-tools 36.0.0、NDK 28.0.13004108 和 Gradle 8.14.2 装进 gitignored 的 `.android-env/`，随后把参数原样转给 `./gradlew`，并重写 `local.properties`。已有 `JAVA_HOME` / `GRADLE_USER_HOME` / `ANDROID_HOME` 时优先复用。首次配置需要下载数 GB 工具链。

```sh
./build.sh assembleDebug                 # debug APK，输出到 app/build/outputs/apk/
./build.sh detekt assembleDebug          # 项目要求的提交前检查 + 构建
./build.sh testDebugUnitTest             # 全部 JVM 单元测试
./build.sh testDebugUnitTest --tests 'live.mehiz.mpvkt.network.SmbStreamServerTest'
./build.sh testDebugUnitTest --tests 'live.mehiz.mpvkt.network.SmbStreamServerTest.testMethod'
./build.sh detekt                        # Kotlin 静态检查；规则在 config/detekt/detekt.yml
./build.sh lintDebug                     # Android lint
./build.sh assemblePreview               # preview：release 配置 + debug 签名
./build.sh assembleRelease               # 未签名 release，CI 中随后单独签名
```

Windows 把 `./build.sh` 换成 `build.bat`。CI 使用已配置环境的 `./gradlew`：`detekt assembleRelease`（`.github/workflows/build.yml`）、`assemblePreview`（nightlies）、`assembleRelease`（release）。仅有 `app/src/test` 下的 JUnit 4 单元测试，没有 `androidTest` 目录；`app/build.gradle.kts` 设置 `unitTests.isReturnDefaultValues = true`，使依赖 `android.util.Log` 的纯 Kotlin 代码也能在 JVM 测试里运行。Detekt 生成 HTML/Markdown 报告；Kotlin/Gradle 代码使用 2 空格缩进、120 字符行宽（`.editorconfig`）。

## 应用外壳与数据

`App.kt` 用 Koin `KoinStartup` 装配模块，并安装 `GlobalExceptionHandler`（崩溃转 `CrashActivity`）。`MainActivity.kt` 是全 Compose 的入口：Navigation 3 的 `rememberNavBackStack` + `NavDisplay`，路由为实现 `presentation/Screen.kt` 的 `@Serializable` object/data class，路由栈通过 `LocalBackStack` 传递给各页面；`HomeScreen` 是起始路由，连接 URL/单文件/目录选择器、历史、网络源和偏好页。两个 Activity 都继承 `AppCompatActivity`。

`di/` 中的模块：`PreferencesModule` 用 `AndroidPreferenceStore`（SharedPreferences）支撑 `preferences/` 下面按领域拆分的偏好类；`DatabaseModule` 创建 Room `MpvKtDatabase`，并把 DAO 包装成 `domain/.../repository` 接口；`NetworkModule` 提供 OkHttp、`NetworkStore`、`RemoteClientFactory`、`SmbStreamServer`、`RemoteFontStager`；`AppModule` 提供宽松的 `Json` 和 `FontConfigManager`；`ViewModelModule` 注册设置/网络/历史的 Koin ViewModel。播放器的 `PlayerViewModel` 是例外：它由 `PlayerViewModelProviderFactory` 直接持有 `PlayerActivity` 引用创建。`MainActivity` 还按间隔在 IO 线程触发 `FontIndexer` 的系统/用户字体目录重新索引。

Room 数据库当前版本为 9，实体是 `PlaybackStateEntity`（进度、速度、音/字幕轨与延迟）、`CustomButtonEntity`、`FontEntity`；迁移是 `database/Migrations.kt` 中的手写 SQL 数组。改动 `@Database` 或实体时，需要同时补迁移。Gradle Room 插件把 schema 写到 `app/schemas/`，但该目录在 `.gitignore` 中，工作区里的 schema JSON 不属于版本控制。偏好值既可直接 `get()/set()`，也通过 `preferences/preference/Preference.kt` 的 `collectAsState` 扩展在 Compose 中观察。

`network/` 支持 WebDAV、FTP、SMB。服务器配置是 `NetworkSource` 的 JSON 列表，经 `NetworkStore` 存在 SharedPreferences；密码只是 Base64 混淆，不是加密。`RemoteClientFactory` 选择客户端；由于 mpv 自带 ffmpeg 不支持 SMB，`SmbStreamServer` 在本机 IPv4 loopback 上提供带随机 token 的 Range HTTP 服务，交给 mpv 的 URL 不包含凭据。`NetworkBrowserScreen` 把选中的文件列表通过 `PlaybackQueueStore` 写入私有磁盘，只在 Intent 中传递随机 token（`QUEUE_REQUEST_EXTRA`），并附带序列化的 `NetworkSource` 与目录用于远程字体准备。AndroidManifest 允许明文流量，局域网 NAS 的普通 HTTP WebDAV/FTP 依赖这一点。

## 播放器架构

`PlayerActivity` 是导出的播放器入口（`singleTask`、支持 PiP、处理 VIEW/SEND 及大量 MIME/协议），不是 Compose 路由。`IntentResolver` 把 content://、fd://、实际路径、分享文本和 URL 统一成 mpv 可打开的字符串，并在启动时验证文件类型；`IntentMediaSource` 处理来源选择。`PlayerActivity` 负责生命周期、音频焦点、MediaSession、屏幕方向、PiP、后台播放绑定、存储授权后的重载、外部字幕、队列与每文件播放状态保存/恢复。mpv 的原生播放列表推进不会经过 `onNewIntent`，因此 `FILE_LOADED` 中通过 `path` 与 `expectedIntentPath` 比较识别“自动换集”，并重新准备字幕和字体（`PlayerActivity.kt:1154`）。

播放器画面与控件是叠放的两层 View：`res/layout/player_layout.xml` 中 `MPVView`（继承依赖库 `BaseMPVView`，持有原生 Surface）在下，`ComposeView`（`binding.controls`）在上。`PlayerActivity` 用 ViewBinding 加载布局，并在 ComposeView 中设置 `PlayerControls`。`ui/player/controls/` 是播放器全部 Compose UI：手势、进度条、四角按钮、sheets、字幕/音频/视频设置 panels 等。

`ui/player/MPVView.kt` 构造实例化的 `is.xyz.mpv.MPV(context, filesDir, cacheDir)`，随后调用 `MPVLib.attach(mpv)`，再按偏好设置初始 option 并观察 mpv 属性。因为 0.1.13 的 `MPV` 构造器即初始化 libmpv，所有 option 必须在构造之后设置。`player/MPVLib.kt` 是关键兼容层：它把旧版静态全局 API 转发到当前唯一的 `MPV` 实例，并提供按属性名缓存的 `propInt/propBoolean/propString/propNode/...` StateFlow、事件流、`EventObserver` 和日志流。`attach()` 会重新订阅所有已有属性；`destroy()` 由 `PlayerActivity.onDestroy()` 调用。Compose 控件和 `PlayerViewModel` 直接读取这些 StateFlow 或调用 `MPVLib.command(...)`/`setProperty*`，Activity 侧则通过 `PlayerObserver` 在 UI 线程接收 `FILE_LOADED`、属性变化和 Lua 的 `user-data/mpvkt/*` 消息。Lua 属性名清单在 `MPVView.observedProps` 中，`PlayerViewModel.handleLuaInvocation` 负责分发。

播放器状态并非全在 ViewModel：`PlayerViewModel` 持有可见性、sheet/panel、gesture seek、亮度/音量、睡眠计时器、自定义按钮、截图等 UI 状态和命令；`PlayerActivity` 持有当前真实路径、队列上下文、远程源、授权弹窗标记和持久化播放状态。拖动进度条的预览 seek 必须经 `PlayerViewModel.previewSeeks` 这个 CONFLATED channel 在 IO 线程执行，不能在 UI 线程逐帧调用同步的 `MPVLib.command`。

字幕字体分两条路径：`FontConfigManager` 在 `filesDir/fonts.conf` 写出 fontconfig 目录清单，让 mpv/libass 原地索引系统字体、用户字体目录和当前视频目录；`FontIndexer`/`FontEntity` 维护按字体内部 family 名建立的索引，`SubtitleFontPipeline` 为 SAF 不可直接读取的字体做缓存复制 fallback，并通过 mpv 日志中的缺字信息触发重试或 `MissingFontsDialog`。`MPVView` 把 `sub-fonts-dir` 指向 `cacheDir/fonts/`，而 `FontConfigManager` 的缓存目录是 `cacheDir/fontconfig/`。播放器启动会并行准备字体和 `loadfile`（有短等待预算，冷索引可稍后重建字幕渲染器），并从同名字幕解析 ASS 字体族；队列自动换集时必须重复这套准备。

配置/脚本不是编译期资源，而是每次播放器启动时写入 `filesDir`：`PlayerActivity.copyMPVAssets()` 调用依赖库的 `Utils.copyAssets()`，`copyMPVConfigFiles()` 优先复制用户选择的 SAF 配置目录，否则把 `AdvancedPreferences` 中的 `mpv.conf`、`input.conf` 文本写成文件；`app/src/main/assets/mpvkt.lua` 被写入 `filesDir/scripts/`，提供 `mpvkt` Lua 接口；自定义按钮的 Lua 内容来自 Room，由 `setupCustomButtons()` 生成 `custombuttons.lua` 并 `load-script`。`docs/CUSTOMBUTTONS.rst` 是该接口及 `script-message call_button_<id>` 的说明。

截图由 `ScreenshotSaver` 调用 mpv 的 `screenshot-to-file`，优先把缓存 PNG 复制进用户授权的 SAF 目录；`PlayerActivity.requestScreenshot()` 负责必要时先打开目录树选择器。PiP 动作/接收器在 `PipActions.kt` 和 `PlayerActivity`，后台播放通知及媒体会话在 `MediaPlaybackService`。语言字符串位于 `res/values` 及 17 个 `values-*` 目录；`androidResources.generateLocaleConfig = true` 生成系统 locale 配置，应用内切换调用 `AppCompatDelegate.setApplicationLocales()`（`AppearancePreferencesScreen.kt`）。新增界面文案应放进 `values/strings.xml`，翻译放在对应语言目录。

## 构建约定

- `build.sh`/`build.bat` 是工具链的统一入口；直接用 `./gradlew` 只适合已经有完整 Android SDK/JDK/NDK 的环境。NDK 仅用于 strip 预编译的 native 库（`libc++_shared` 带调试信息），项目不编译自己的 C/C++。
- Gradle 配置时运行 `git rev-list --count HEAD` 和 `git rev-parse --short HEAD`，生成 `BuildConfig.GIT_COUNT` / `GIT_SHA`、preview/debug 的 `versionNameSuffix`；nightly CI 使用 `fetch-depth: 0` 获取完整历史。构建需处于可读的 git 工作树中。
- `build.gradle.kts` 开启按四种 ABI 拆分且额外生成 universal APK；`androidComponents.onVariants` 把 ABI 编号附加到 versionCode。输出文件名因此带 `arm64-v8a`、`universal` 等后缀。debug/preview/release 三种变体分别加 `.debug`/`.preview` 后缀或不加后缀；release 开启 minify 与资源压缩，preview 复用 release 配置但使用 debug 签名。
- `app/src/main/AndroidManifest.xml` 声明了 `MANAGE_EXTERNAL_STORAGE` 与 `usesCleartextTraffic="true"`；存储访问相关逻辑分为首页提示横幅与播放器内针对单个 content:// 文件的上下文提示，后者还需要在返回权限设置页后重新 `loadfile` 才能发现同目录字幕。
