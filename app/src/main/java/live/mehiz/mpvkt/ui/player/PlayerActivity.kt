package live.mehiz.mpvkt.ui.player

import android.Manifest
import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import android.util.Rational
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.text.isDigitsOnly
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import com.github.k1rakishou.fsaf.FileManager
import `is`.xyz.mpv.MPVNode
import `is`.xyz.mpv.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.database.entities.CustomButtonEntity
import live.mehiz.mpvkt.database.entities.PlaybackStateEntity
import live.mehiz.mpvkt.databinding.PlayerLayoutBinding
import live.mehiz.mpvkt.domain.playbackstate.repository.PlaybackStateRepository
import live.mehiz.mpvkt.player.FontIndexer
import live.mehiz.mpvkt.player.MPVLib
import live.mehiz.mpvkt.preferences.AdvancedPreferences
import live.mehiz.mpvkt.preferences.AudioPreferences
import live.mehiz.mpvkt.preferences.GesturePreferences
import live.mehiz.mpvkt.preferences.PlayerPreferences
import live.mehiz.mpvkt.preferences.SubtitlesPreferences
import live.mehiz.mpvkt.ui.player.controls.PlayerControls
import live.mehiz.mpvkt.ui.theme.MpvKtTheme
import org.koin.android.ext.android.inject
import java.io.File

@Suppress("TooManyFunctions", "LargeClass")
class PlayerActivity : AppCompatActivity() {

  private val viewModel: PlayerViewModel by viewModels<PlayerViewModel> { PlayerViewModelProviderFactory(this) }
  private val binding by lazy { PlayerLayoutBinding.inflate(layoutInflater) }
  private val playerObserver by lazy { PlayerObserver(this) }
  private val playbackStateRepository: PlaybackStateRepository by inject()
  val player by lazy { binding.player }
  val windowInsetsController by lazy { WindowCompat.getInsetsController(window, window.decorView) }
  val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
  private var mediaSession: MediaSession? = null
  private val playerPreferences: PlayerPreferences by inject()
  private val audioPreferences: AudioPreferences by inject()
  private val subtitlesPreferences: SubtitlesPreferences by inject()
  private val advancedPreferences: AdvancedPreferences by inject()
  private val gesturePreferences: GesturePreferences by inject()
  private val fileManager: FileManager by inject()
  private val fontIndexer: FontIndexer by inject()

  private val attemptedFontFamilies = mutableSetOf<String>()

  private var fileName = ""
  private var mediaPlaybackService: MediaPlaybackService? = null
  private var serviceBound = false

  private var audioFocusRequest: AudioFocusRequestCompat? = null
  private var restoreAudioFocus: () -> Unit = {}

  private var pipRect: android.graphics.Rect? = null
  private val storagePermissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
  val isPipSupported by lazy {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
      false
    } else {
      packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }
  }
  private var pipReceiver: BroadcastReceiver? = null

  private val noisyReceiver = object : BroadcastReceiver() {
    var initialized = false
    override fun onReceive(context: Context?, intent: Intent?) {
      if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
        viewModel.pause()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    if (!isSupportedPlayable(intent)) {
      rejectUnsupportedFile()
      finish()
      return
    }
    setContentView(binding.root)

    setupMPV()
    setupAudio()
    setupMediaSession()
    lifecycleScope.launch(Dispatchers.IO) {
      val playable = getPlayableUri(intent)
      stageVideoFonts(playable)
      withContext(Dispatchers.Main) {
        playable?.let(player::playFile)
      }
    }
    setOrientation()

    binding.controls.setContent {
      MpvKtTheme {
        PlayerControls(
          viewModel = viewModel,
          onBackPress = ::finish,
          modifier = Modifier.onGloballyPositioned {
            pipRect = it.boundsInWindow().toAndroidRect()
          },
        )
      }
    }
  }

  private fun getPlayableUri(intent: Intent): String? {
    val uri = parsePathFromIntent(intent)
    return if (uri?.startsWith("content://") == true) uri.toUri().openContentFd(this) else uri
  }

  override fun onDestroy() {
    Log.d(TAG, "Exiting")
    audioFocusRequest?.let {
      AudioManagerCompat.abandonAudioFocusRequest(audioManager, it)
    }
    audioFocusRequest = null
    mediaSession?.release()
    if (noisyReceiver.initialized) {
      unregisterReceiver(noisyReceiver)
      noisyReceiver.initialized = false
    }

    player.isExiting = true
    if (isFinishing) {
      MPVLib.command("stop")
    }
    MPVLib.removeObserver(playerObserver)
    MPVLib.destroy()

    super.onDestroy()
  }

  override fun onPause() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
      !isInPictureInPictureMode &&
      !playerPreferences.automaticBackgroundPlayback.get()
    ) {
      viewModel.pause()
    }
    saveVideoPlaybackState(fileName)
    super.onPause()
  }

  override fun finish() {
    setReturnIntent()
    super.finish()
  }

  override fun onStop() {
    saveVideoPlaybackState(fileName)
    if (!serviceBound && playerPreferences.automaticBackgroundPlayback.get()) {
      startBackgroundPlayback()
    } else {
      viewModel.pause()
      if (serviceBound) {
        unbindService(serviceConnection)
        serviceBound = false
      }
    }
    window.attributes.screenBrightness.let {
      if (playerPreferences.rememberBrightness.get() && it != -1f) {
        playerPreferences.defaultBrightness.set(it)
      }
    }
    super.onStop()
  }

  @SuppressLint("NewApi")
  override fun onUserLeaveHint() {
    if (isPipSupported && viewModel.paused == false && playerPreferences.automaticallyEnterPip.get()) {
      enterPictureInPictureMode()
    }
    super.onUserLeaveHint()
  }

  @SuppressLint("NewApi")
  override fun onBackPressed() {
    if (isPipSupported && viewModel.paused == false && playerPreferences.automaticallyEnterPip.get()) {
      if (viewModel.sheetShown.value == Sheets.None && viewModel.panelShown.value == Panels.None) {
        enterPictureInPictureMode()
      }
    } else {
      super.onBackPressed()
    }
  }

  override fun onStart() {
    super.onStart()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isPipSupported) {
      setPictureInPictureParams(createPipParams())
    }
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    binding.root.systemUiVisibility =
      View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
      View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
      View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
      View.SYSTEM_UI_FLAG_LOW_PROFILE
    windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
    windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      window.attributes.layoutInDisplayCutoutMode = if (playerPreferences.drawOverDisplayCutout.get()) {
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
      } else {
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
      }
    }

    if (playerPreferences.rememberBrightness.get()) {
      playerPreferences.defaultBrightness.get().let {
        if (it != -1f) viewModel.changeBrightnessTo(it)
      }
    }

    if (serviceBound) {
      endBackgroundPlayback()
    }
  }

  private fun copyMPVAssets() {
    Utils.copyAssets(this@PlayerActivity)
    copyMPVScripts()
    copyMPVConfigFiles()
  }

  private fun setupMPV() {
    copyMPVAssets()
    requestStoragePermission()
    player.initialize(filesDir.path, cacheDir.path)
    MPVLib.attach(player.mpv)
    MPVLib.addObserver(playerObserver)
    MPVLib.addLogObserver(object : MPVLib.LogObserver {
      private val fontRequestRegex = Regex("""\(([^,()]+),\s*\d+,\s*\d+\)""")
      private val ignoredFontFamilies = setOf("sans-serif", "sans serif", "mpv-osd-symbols")

      override fun logMessage(prefix: String, level: Int, text: String) {
        Log.i(TAG, "mpv [$prefix/$level] $text")
        if (level <= MPVLib.mpvLogLevel.MPV_LOG_LEVEL_ERROR) {
          PlayerActivity.lastMpvError = "$prefix: $text"
        }
        val family = extractMissingFont(text) ?: return
        runOnUiThread { handleMissingFont(family) }
      }

      private fun extractMissingFont(text: String): String? {
        val isFontFallback = "Using default font" in text
        val isFontFailure = "not found" in text || "failed" in text
        val family = if (isFontFallback || isFontFailure) {
          fontRequestRegex.find(text)?.groupValues?.get(1)?.trim()
        } else {
          null
        }
        val ignored = family.isNullOrEmpty() ||
          family.lowercase() in ignoredFontFamilies ||
          family == subtitlesPreferences.font.get()
        return if (ignored) null else family
      }
    })
  }

  private fun requestStoragePermission() {
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2) return
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
      PackageManager.PERMISSION_GRANTED
    ) {
      return
    }
    storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
  }

  private fun setupAudio() {
    audioPreferences.audioChannels.get().let { MPVLib.setPropertyString(it.property, it.value) }

    val request = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN).also {
      it.setAudioAttributes(
        AudioAttributesCompat.Builder().setUsage(AudioAttributesCompat.USAGE_MEDIA)
          .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC).build(),
      )
      it.setOnAudioFocusChangeListener(audioFocusChangeListener)
    }.build()
    AudioManagerCompat.requestAudioFocus(audioManager, request).let {
      if (it == AudioManager.AUDIOFOCUS_REQUEST_FAILED) return@let
      audioFocusRequest = request
    }
  }

  private fun copyMPVConfigFiles() {
    val applicationPath = filesDir.path
    try {
      val mpvConf = fileManager.fromUri(advancedPreferences.mpvConfStorageUri.get().toUri())
        ?: error("User hasn't set any mpvConfig directory")
      if (!fileManager.exists(mpvConf)) error("Couldn't access mpv configuration directory")
      fileManager.copyDirectoryWithContent(mpvConf, fileManager.fromPath(applicationPath), true)
    } catch (e: Exception) {
      File("$applicationPath/mpv.conf")
        .also { if (!it.exists()) it.createNewFile() }
        .writeText(advancedPreferences.mpvConf.get())
      File("$applicationPath/input.conf")
        .also { if (!it.exists()) it.createNewFile() }
        .writeText(advancedPreferences.inputConf.get())
      Log.e("PlayerActivity", "Couldn't copy mpv configuration files: ${e.message}")
    }
  }

  private fun copyMPVScripts() {
    val mpvktLua = assets.open("mpvkt.lua")
    val applicationPath = filesDir.path

    val scriptsDir = fileManager.createDir(fileManager.fromPath(applicationPath), "scripts")!!

    fileManager.deleteContent(scriptsDir)

    File("$scriptsDir/mpvkt.lua")
      .also { if (!it.exists()) it.createNewFile() }
      .writeText(mpvktLua.bufferedReader().readText())
  }

  fun setupCustomButtons(buttons: List<CustomButtonEntity>) {
    val applicationPath = filesDir.path

    val scriptsDir = fileManager.createDir(fileManager.fromPath(applicationPath), "scripts")!!

    val customButtonsContent = buildString {
      appendLine("local lua_modules = mp.find_config_file('scripts')")
      appendLine("if lua_modules then")
      appendLine("package.path = package.path .. ';' .. lua_modules .. '/?.lua;' .. lua_modules .. '/?/init.lua'")
      appendLine("end")
      appendLine("local mpvkt = require 'mpvkt'")
      buttons.forEach { button ->
        appendLine("function button${button.id}()")
        appendLine(button.content)
        appendLine("end")
        appendLine("mp.register_script_message('call_button_${button.id}', button${button.id})")
        appendLine("function button${button.id}long()")
        appendLine(button.longPressContent)
        appendLine("end")
        appendLine("mp.register_script_message('call_button_${button.id}_long', button${button.id}long)")
      }
    }

    val file = File("$scriptsDir/custombuttons.lua")
      .also { if (!it.exists()) it.createNewFile() }

    file.writeText(customButtonsContent)

    MPVLib.command("load-script", file.absolutePath)
  }

  /**
   * Stages the font family picked in the typography card (used as the default
   * subtitle font) through the index — no full-folder copies anywhere.
   */
  fun stageSubFont(family: String) {
    lifecycleScope.launch(Dispatchers.IO) {
      val destDir = fontsCacheDir()
      ensureBundledFont(destDir)
      refreshFontIndex(force = true)
      stageIndexedFont(family, destDir)
      MPVLib.setPropertyString("sub-font", family)
    }
  }

  /**
   * Feeds libass with every font it may need for the current video: fonts
   * bundled next to the video plus, for small libraries, the whole user font
   * folder. Larger libraries resolve per family through the index when libass
   * reports a missing font. Runs before the first play.
   */
  private suspend fun stageVideoFonts(videoPath: String?) {
    val destDir = fontsCacheDir()
    ensureBundledFont(destDir)
    refreshFontIndex()
    stageSiblingFonts(videoPath, destDir)
  }

  /**
   * Called from the mpv log observer when libass cannot resolve a font family.
   * If the font exists in the index (large user folder or system fonts) it is
   * staged and the subtitle renderer reloads; otherwise the missing-font dialog
   * gets the name.
   */
  fun handleMissingFont(family: String) {
    val trimmed = family.trim()
    if (trimmed.isEmpty() || !attemptedFontFamilies.add(trimmed)) return
    lifecycleScope.launch(Dispatchers.IO) {
      runCatching {
        val staged = stageIndexedFont(trimmed, fontsCacheDir())
        if (staged) {
          MPVLib.command("sub-reload")
        } else {
          viewModel.reportMissingFont(trimmed)
        }
      }
    }
  }

  private fun fontsCacheDir(): File = File(cacheDir, "fonts").apply { mkdirs() }

  private suspend fun refreshFontIndex(force: Boolean = false) {
    val now = System.currentTimeMillis()
    if (!force && now - subtitlesPreferences.fontIndexScanAt.get() < FONT_INDEX_SCAN_INTERVAL) return
    runCatching {
      subtitlesPreferences.fontsFolder.get().takeIf { it.isNotBlank() }?.let {
        fontIndexer.reindexUserFolder(it)
      }
      if (subtitlesPreferences.useSystemFonts.get()) fontIndexer.reindexSystemFonts()
      subtitlesPreferences.fontIndexScanAt.set(now)
    }
  }

  private suspend fun stageSiblingFonts(videoPath: String?, destDir: File) {
    val videoFile = videoPath
      ?.takeUnless { it.startsWith("fd://") }
      ?.let { runCatching { File(it) }.getOrNull() }
      ?.takeIf { it.isFile } ?: return
    val parent = videoFile.parentFile ?: return
    val fontDirs = sequenceOf(parent, File(parent, "fonts")).filter { it.isDirectory }
    fontDirs.forEach { dir ->
      dir.listFiles { file -> file.isFile && FontIndexer.FONT_EXTENSIONS.matches(file.name) }
        ?.forEach { font ->
          val target = File(destDir, font.name)
          if (!target.exists() || target.length() != font.length()) {
            runCatching { font.copyTo(target, overwrite = true) }
          }
        }
    }
  }

  private suspend fun stageIndexedFont(family: String, destDir: File): Boolean {
    val paths = fontIndexer.findFontPaths(family)
    if (paths.isEmpty()) return false
    var copied = false
    paths.forEach { path ->
      val targetName = path.substringAfterLast('/')
      val target = File(destDir, targetName)
      if (target.length() > 0) return@forEach
      copied = copied or copyIndexedFile(path, target)
    }
    return copied
  }

  private suspend fun copyIndexedFile(virtualPath: String, target: File): Boolean {
    val source = when {
      virtualPath.startsWith("/") -> {
        val file = File(virtualPath)
        if (file.isFile) ({ file.inputStream() }) else null
      }
      else -> {
        val folderUri = subtitlesPreferences.fontsFolder.get().takeIf { it.isNotBlank() }
        val root = folderUri?.let { fileManager.fromUri(it.toUri()) }
        root?.let { fontIndexer.collectFontFiles(it).firstOrNull { f -> f.path == virtualPath } }?.openStream
      }
    } ?: return false
    val copied = runCatching {
      source()?.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
      } != null
    }.getOrDefault(false)
    return copied && target.length() > 0
  }

  private fun ensureBundledFont(destDir: File) {
    val subfont = File(destDir, "subfont.ttf")
    if (subfont.exists()) return
    runCatching {
      resources.assets.open("subfont.ttf").copyTo(subfont.outputStream())
    }
  }

  private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener {
    when (it) {
      AudioManager.AUDIOFOCUS_LOSS,
      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
      -> {
        val oldRestore = restoreAudioFocus
        val wasPlayerPaused = viewModel.paused ?: false
        viewModel.pause()
        restoreAudioFocus = {
          oldRestore()
          if (!wasPlayerPaused) viewModel.unpause()
        }
      }

      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
        MPVLib.command("multiply", "volume", "0.5")
        restoreAudioFocus = {
          MPVLib.command("multiply", "volume", "2")
        }
      }

      AudioManager.AUDIOFOCUS_GAIN -> {
        restoreAudioFocus()
        restoreAudioFocus = {}
      }

      AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
        Log.d("PlayerActivity", "didn't get audio focus")
      }
    }
  }

  override fun onResume() {
    super.onResume()

    viewModel.currentVolume.update {
      audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).also {
        if (it < viewModel.maxVolume) viewModel.changeMPVVolumeTo(100)
      }
    }
  }

  private val serviceConnection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
      val binder = service as MediaPlaybackService.MediaPlaybackBinder
      mediaPlaybackService = binder.getService()
      serviceBound = true

      fileName.let { title ->
        val artist = MPVLib.getPropertyString("metadata/artist") ?: ""
        mediaPlaybackService?.setMediaInfo(title = title, artist = artist, thumbnail = MPVLib.grabThumbnail(1080))
      }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
      mediaPlaybackService = null
      serviceBound = false
    }
  }

  private fun startBackgroundPlayback() {
    val intent = Intent(this, MediaPlaybackService::class.java)
    startService(intent)
    bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
  }

  private fun endBackgroundPlayback() {
    stopService(Intent(this, MediaPlaybackService::class.java))
    mediaPlaybackService = null
    serviceBound = false
  }

  private fun setIntentExtras(extras: Bundle?) {
    if (extras == null) return

    extras.getString("title")?.let { MPVLib.setPropertyString("force-media-title", it) }
    MPVLib.setPropertyInt("time-pos", extras.getInt("position", 0) / 1000)

    // subtitles
    if (extras.containsKey("subs")) {
      val subList = Utils.getParcelableArray<Uri>(extras, "subs")
      val subsToEnable = Utils.getParcelableArray<Uri>(extras, "subs.enable")

      for (suburi in subList) {
        val subfile = suburi.resolveUri(this) ?: continue
        val flag = if (subsToEnable.any { it == suburi }) "select" else "auto"

        Log.v(TAG, "Adding subtitles from intent extras: $subfile")
        MPVLib.command("sub-add", subfile, flag)
      }
    }

    extras.getStringArray("headers")?.let { headers ->
      if (headers[0].startsWith("User-Agent", true)) MPVLib.setPropertyString("user-agent", headers[1])
      val headersString = headers.asSequence().drop(2).chunked(2).associate { it[0] to it[1] }
        .map { "${it.key}: ${it.value.replace(",", "\\,")}" }.joinToString(",")
      MPVLib.setPropertyString("http-header-fields", headersString)
    }
  }

  @Suppress("NestedBlockDepth")
  private fun parsePathFromIntent(intent: Intent): String? {
    return when (intent.action) {
      Intent.ACTION_VIEW -> intent.data?.resolveUri(this)
      Intent.ACTION_SEND -> {
        if (intent.hasExtra(Intent.EXTRA_STREAM)) {
          intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.resolveUri(this)
        } else {
          intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
            val uri = it.trim().toUri()
            if (uri.isHierarchical && !uri.isRelative) uri.resolveUri(this) else null
          }
        }
      }

      else -> intent.getStringExtra("uri")
    }
  }

  private fun isSupportedPlayable(intent: Intent): Boolean {
    val uri = parsePathFromIntent(intent) ?: return true
    val extension = uri.toUri().lastPathSegment?.substringBefore('?')?.substringAfterLast('.')?.lowercase()
    val mime = intent.type
    val mimeOk = mime == null || mime.startsWith("video/") ||
      mime.startsWith("audio/") || mime.startsWith("image/") ||
      mime == "application/octet-stream" || mime.startsWith("text/")
    val extensionOk = extension == null || extension in videoExtensions ||
      extension in audioExtensions || extension in imageExtensions
    return mimeOk && extensionOk
  }

  private fun rejectUnsupportedFile() {
    Toast.makeText(this, R.string.error_unsupported_file, Toast.LENGTH_LONG).show()
  }

  private fun getFileName(intent: Intent): String {
    val uri = if (intent.type == "text/plain") {
      intent.getStringExtra(Intent.EXTRA_TEXT)!!.toUri()
    } else {
      (intent.data ?: intent.getParcelableExtra(Intent.EXTRA_STREAM))
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && uri != null) {
      val cursor = contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null)
      if (cursor?.moveToFirst() == true) return cursor.getString(0).also { cursor.close() }
    }
    return uri?.lastPathSegment?.substringAfterLast("/") ?: uri?.path ?: ""
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      if (!isInPictureInPictureMode) {
        viewModel.changeVideoAspect(playerPreferences.videoAspect.get())
      } else {
        viewModel.hideControls()
      }
    }
    super.onConfigurationChanged(newConfig)
  }

  // a bunch of observers
  @Suppress("UnusedParameter")
  internal fun onObserverEvent(property: String, value: Long) {
    if (player.isExiting) return
  }

  @Suppress("UnusedParameter")
  internal fun onObserverEvent(property: String) {
    if (player.isExiting) return
  }

  internal fun onObserverEvent(property: String, value: Boolean) {
    if (player.isExiting) return
    when (property) {
      "pause" if value -> window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      "pause" -> window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      "eof-reached" if value && playerPreferences.closeAfterReachingEndOfVideo.get() -> finishAndRemoveTask()
    }
  }

  internal fun onObserverEvent(property: String, value: String) {
    if (player.isExiting) return
    when (property.substringBeforeLast("/")) {
      "user-data/mpvkt" -> viewModel.handleLuaInvocation(property, value)
    }
  }

  @Suppress("UnusedParameter")
  internal fun onObserverEvent(property: String, value: MPVNode) {
    if (player.isExiting) return
  }

  @SuppressLint("NewApi")
  @Suppress("UnusedParameter")
  internal fun onObserverEvent(property: String, value: Double) {
    if (player.isExiting) return
    when (property) {
      "video-params/aspect" -> if (isPipSupported) createPipParams()
    }
  }

  internal fun event(eventId: Int) {
    if (player.isExiting) return
    when (eventId) {
      MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED -> {
        fileName = getFileName(intent)
        setIntentExtras(intent.extras)
        val mediaTitle = MPVLib.getPropertyString("media-title")
        if (mediaTitle.isNullOrBlank() || mediaTitle.isDigitsOnly()) {
          MPVLib.setPropertyString("media-title", fileName)
        }
        lifecycleScope.launch(Dispatchers.IO) {
          loadVideoPlaybackState(fileName)
        }
        setOrientation()
        viewModel.changeVideoAspect(playerPreferences.videoAspect.get())
      }

      MPVLib.mpvEventId.MPV_EVENT_PLAYBACK_RESTART -> player.isExiting = false
    }
  }

  private fun saveVideoPlaybackState(mediaTitle: String) {
    if (mediaTitle.isBlank()) return
    lifecycleScope.launch(Dispatchers.IO) {
      val oldState = playbackStateRepository.getVideoDataByTitle(fileName)
      Log.d(TAG, "Saving playback state")
      playbackStateRepository.upsert(
        PlaybackStateEntity(
          mediaTitle = mediaTitle,
          lastPosition = if (playerPreferences.savePositionOnQuit.get()) {
            val pos = viewModel.pos ?: 0
            val duration = viewModel.duration ?: 0
            if (pos < duration - 1) pos else 0
          } else {
            oldState?.lastPosition ?: 0
          },
          playbackSpeed = MPVLib.getPropertyDouble("speed")!!,
          sid = player.sid,
          subDelay = (MPVLib.getPropertyDouble("sub-delay")!! * 1000).toInt(),
          subSpeed = MPVLib.getPropertyDouble("sub-speed")!!,
          secondarySid = player.secondarySid,
          secondarySubDelay = (MPVLib.getPropertyDouble("secondary-sub-delay")!! * 1000).toInt(),
          aid = player.aid,
          audioDelay = (MPVLib.getPropertyDouble("audio-delay")!! * 1000).toInt(),
        ),
      )
    }
  }

  private suspend fun loadVideoPlaybackState(mediaTitle: String) {
    if (mediaTitle.isBlank()) return
    val state = playbackStateRepository.getVideoDataByTitle(mediaTitle)
    val getDelay: (Int, Int?) -> Double = { preferenceDelay, stateDelay ->
      (stateDelay ?: preferenceDelay) / 1000.0
    }
    val subDelay = getDelay(subtitlesPreferences.defaultSubDelay.get(), state?.subDelay)
    val secondarySubDelay = getDelay(subtitlesPreferences.defaultSecondarySubDelay.get(), state?.secondarySubDelay)
    val audioDelay = getDelay(audioPreferences.defaultAudioDelay.get(), state?.audioDelay)
    // Never-played files have no meaningful track/delay choices; restoring
    // their zero state would force mpv's auto-selected subtitle/audio tracks
    // off, so only restore once the video was actually watched.
    state?.takeIf { it.lastPosition > 0 }?.let {
      player.sid = it.sid
      player.secondarySid = it.secondarySid
      player.aid = it.aid
      MPVLib.setPropertyDouble("sub-delay", subDelay)
      MPVLib.setPropertyDouble("secondary-sub-delay", secondarySubDelay)
      MPVLib.setPropertyDouble("speed", it.playbackSpeed)
      MPVLib.setPropertyDouble("audio-delay", audioDelay)
    }
    if (playerPreferences.savePositionOnQuit.get()) {
      state?.lastPosition?.let { if (it != 0) MPVLib.setPropertyInt("time-pos", it) }
    }
    MPVLib.setPropertyDouble("sub-speed", state?.subSpeed ?: subtitlesPreferences.defaultSubSpeed.get().toDouble())
  }

  private fun setReturnIntent() {
    Log.d(TAG, "setting return intent")
    setResult(
      RESULT_OK,
      Intent(RESULT_INTENT).apply {
        viewModel.pos?.let { putExtra("position", it * 1000) }
        viewModel.duration?.let { putExtra("duration", it * 1000) }
      },
    )
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    if (!isSupportedPlayable(intent)) {
      rejectUnsupportedFile()
      return
    }

    getPlayableUri(intent)?.let { MPVLib.command("loadfile", it) }
    setIntent(intent)
  }

  @RequiresApi(Build.VERSION_CODES.O)
  fun createPipParams(): PictureInPictureParams {
    val builder = PictureInPictureParams.Builder()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      builder.setTitle(MPVLib.getPropertyString("media-title") ?: fileName)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val autoEnter = playerPreferences.automaticallyEnterPip.get()
      builder.setAutoEnterEnabled(viewModel.paused == false && autoEnter)
      builder.setSeamlessResizeEnabled(viewModel.paused == false && autoEnter)
    }
    builder.setActions(createPipActions(this, viewModel.paused == true))
    builder.setSourceRectHint(pipRect)
    MPVLib.getPropertyInt("video-params/h")?.let {
      val height = it
      val width = it * player.getVideoOutAspect()!!
      val rational = Rational(height, width.toInt()).toFloat()
      if (rational in 0.42..2.38) builder.setAspectRatio(Rational(width.toInt(), height))
    }
    return builder.build()
  }

  override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
    if (!isInPictureInPictureMode) {
      pipReceiver?.let {
        unregisterReceiver(pipReceiver)
        pipReceiver = null
      }
      super.onPictureInPictureModeChanged(false, newConfig)
      return
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      setPictureInPictureParams(createPipParams())
    }
    viewModel.hideControls()
    viewModel.hideSeekBar()
    viewModel.isBrightnessSliderShown.update { false }
    viewModel.isVolumeSliderShown.update { false }
    viewModel.sheetShown.update { Sheets.None }
    pipReceiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null || intent.action != PIP_INTENTS_FILTER) return
        when (intent.getIntExtra(PIP_INTENT_ACTION, 0)) {
          PIP_PAUSE -> viewModel.pause()
          PIP_PLAY -> viewModel.unpause()
          PIP_FF -> viewModel.handleRightDoubleTap()
          PIP_FR -> viewModel.handleLeftDoubleTap()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          setPictureInPictureParams(createPipParams())
        }
      }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      registerReceiver(pipReceiver, IntentFilter(PIP_INTENTS_FILTER), RECEIVER_NOT_EXPORTED)
    } else {
      registerReceiver(pipReceiver, IntentFilter(PIP_INTENTS_FILTER))
    }
    super.onPictureInPictureModeChanged(true, newConfig)
  }

  private fun setOrientation() {
    requestedOrientation = when (playerPreferences.orientation.get()) {
      PlayerOrientation.Free -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
      PlayerOrientation.Video -> if ((player.getVideoOutAspect() ?: 0.0) > 1.0) {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
      } else {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
      }

      PlayerOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
      PlayerOrientation.ReversePortrait -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
      PlayerOrientation.SensorPortrait -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
      PlayerOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
      PlayerOrientation.ReverseLandscape -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
      PlayerOrientation.SensorLandscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
  }

  override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    when (keyCode) {
      KeyEvent.KEYCODE_VOLUME_UP -> {
        viewModel.changeVolumeBy(1)
        viewModel.displayVolumeSlider()
      }

      KeyEvent.KEYCODE_VOLUME_DOWN -> {
        viewModel.changeVolumeBy(-1)
        viewModel.displayVolumeSlider()
      }

      KeyEvent.KEYCODE_DPAD_RIGHT -> viewModel.handleLeftDoubleTap()
      KeyEvent.KEYCODE_DPAD_LEFT -> viewModel.handleRightDoubleTap()
      KeyEvent.KEYCODE_SPACE -> viewModel.pauseUnpause()
      KeyEvent.KEYCODE_MEDIA_STOP -> finishAndRemoveTask()

      KeyEvent.KEYCODE_MEDIA_REWIND -> viewModel.handleLeftDoubleTap()
      KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> viewModel.handleRightDoubleTap()

      // other keys should be bound by the user in input.conf ig
      else -> {
        event?.let { player.onKey(it) }
        super.onKeyDown(keyCode, event)
      }
    }
    return true
  }

  override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
    if (player.onKey(event!!)) return true
    return super.onKeyUp(keyCode, event)
  }

  private fun setupMediaSession() {
    val previousAction = gesturePreferences.mediaPreviousGesture.get()
    val playAction = gesturePreferences.mediaPlayGesture.get()
    val nextAction = gesturePreferences.mediaNextGesture.get()

    mediaSession = MediaSession(this, "PlayerActivity").apply {
      setCallback(
        object : MediaSession.Callback() {
          override fun onPlay() {
            when (playAction) {
              SingleActionGesture.None -> {}
              SingleActionGesture.Seek -> {}
              SingleActionGesture.PlayPause -> {
                super.onPlay()
                viewModel.unpause()
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
              }

              SingleActionGesture.Custom -> {
                MPVLib.command("keypress", CustomKeyCodes.MediaPlay.keyCode)
              }
            }
          }

          override fun onPause() {
            when (playAction) {
              SingleActionGesture.None -> {}
              SingleActionGesture.Seek -> {}
              SingleActionGesture.PlayPause -> {
                super.onPause()
                viewModel.pause()
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
              }

              SingleActionGesture.Custom -> {
                MPVLib.command("keypress", CustomKeyCodes.MediaPlay.keyCode)
              }
            }
          }

          override fun onSkipToPrevious() {
            when (previousAction) {
              SingleActionGesture.None -> {}
              SingleActionGesture.Seek -> {
                viewModel.leftSeek()
              }

              SingleActionGesture.PlayPause -> {
                viewModel.pauseUnpause()
              }

              SingleActionGesture.Custom -> {
                MPVLib.command("keypress", CustomKeyCodes.MediaPrevious.keyCode)
              }
            }
          }

          override fun onSkipToNext() {
            when (nextAction) {
              SingleActionGesture.None -> {}
              SingleActionGesture.Seek -> {
                viewModel.rightSeek()
              }

              SingleActionGesture.PlayPause -> {
                viewModel.pauseUnpause()
              }

              SingleActionGesture.Custom -> {
                MPVLib.command("keypress", CustomKeyCodes.MediaNext.keyCode)
              }
            }
          }

          override fun onStop() {
            super.onStop()
            isActive = false
            this@PlayerActivity.onStop()
          }
        },
      )
      setPlaybackState(
        PlaybackState.Builder()
          .setActions(
            PlaybackState.ACTION_PLAY or
              PlaybackState.ACTION_PAUSE or
              PlaybackState.ACTION_STOP or
              PlaybackState.ACTION_SKIP_TO_PREVIOUS or
              PlaybackState.ACTION_SKIP_TO_NEXT,
          )
          .build(),
      )
      isActive = true
    }

    val filter = IntentFilter().apply { addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY) }
    registerReceiver(noisyReceiver, filter)
    noisyReceiver.initialized = true
  }

  companion object {
    // action of result intent
    private const val RESULT_INTENT = "live.ywpc05.mpvkt.ui.player.PlayerActivity.result"

    @Volatile
    var lastMpvError: String? = null
  }
}

const val TAG = "mpvKt"
