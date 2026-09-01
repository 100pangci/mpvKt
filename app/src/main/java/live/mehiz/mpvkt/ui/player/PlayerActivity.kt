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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
  private var restoredTrackState = false
  private var autoSubSelectedForThisVideo = false
  private var fontRefreshOnMissDone = false

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
      startPlaybackFlow(intent)
    }
    lifecycleScope.launch {
      MPVLib.eventFlow("track-list").collect {
        delay(400)
        autoSelectSubTrackIfNeeded()
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

  /**
   * Resolves the video, finds its sibling subtitle and starts playback.
   * playFile is issued unconditionally and first: font setup runs in a
   * fully detached job that reloads the subtitle renderer once it is done,
   * so no font work can ever delay the first frame.
   */
  private suspend fun CoroutineScope.startPlaybackFlow(intent: Intent) {
    val playable = getPlayableUri(intent)
    Log.i(TAG, "playback flow: playable=$playable")
    if (playable == null) return
    var videoPath = playable.takeUnless { it.startsWith("fd://") }
    if (videoPath == null) {
      // fd:// has no directory context: resolve the real video location from
      // the content URI so sibling fonts and subtitles still resolve.
      videoPath = realVideoFileFromContentUri(intent.data)?.absolutePath
        ?: realDirFromContentUri(intent.data)?.let { dir ->
          getFileName(intent).takeIf { it.isNotBlank() }
            ?.let { name -> File(dir, name).takeIf { f -> f.isFile } }?.absolutePath
        }
    }
    val siblingSubPath = videoPath?.let(::File)?.takeIf { it.isFile }
      ?.let { guessSiblingSubtitle(it) }?.absolutePath
    Log.i(TAG, "playback flow: sibling=$siblingSubPath")
    launch {
      runCatching {
        withTimeoutOrNull(FONT_SETUP_TIMEOUT_MS) {
          val stagedSub = siblingSubPath?.let { preloadSubtitleFonts(it) } ?: false
          val stagedVideo = stageVideoFonts(videoPath)
          // Only reload the renderer when fonts actually landed; a reload
          // against an idle player would just log "not initialized".
          if (stagedSub || stagedVideo) MPVLib.command("sub-reload")
        }
      }
      Log.i(TAG, "playback flow: font setup finished")
    }
    withContext(Dispatchers.Main) {
      // NOT player.playFile(): that only stores the path for the surface
      // callback to consume ONCE — if the surface already exists the file
      // would never load (black screen). Issue the command directly, exactly
      // like onNewIntent does; it is valid in any mpv state.
      MPVLib.command("loadfile", playable)
      siblingSubPath?.let { MPVLib.command("sub-add", it, "auto") }
    }
    Log.i(TAG, "playback flow: playFile issued")
  }

  private fun getPlayableUri(intent: Intent): String? {
    val data = intent.data
    val fromFd = data?.takeIf { it.scheme == "content" }?.let { uri ->
      runCatching { uri.openContentFd(this) }.getOrNull()
        // The provider denied access (no grant, revoked permission, offline
        // cloud): with All-Files-Access the file is still reachable directly.
        ?: contentUriToRealFile(uri)?.absolutePath
    }
    return fromFd ?: parsePathFromIntent(intent)?.let { uri ->
      if (uri.startsWith("content://")) uri.toUri().openContentFd(this) else uri
    }
  }

  private fun contentUriToRealFile(uri: Uri): File? {
    val relative = uri.path
      ?.takeIf { it.startsWith("/document/primary:") }
      ?.removePrefix("/document/primary:")
      ?: return null
    return File("/storage/emulated/0", relative).takeIf { it.isFile }
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
      // The family comes straight from the index list: an incremental
      // refresh (throttled) is enough, no forced full scan per pick.
      refreshFontIndex()
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
  /**
   * Newer mpv no longer auto-selects subtitle tracks that carry neither a
   * default flag nor a matching language. For sessions without a restoreable
   * track state, pick the first (default-flagged if any) subtitle track so
   * embedded subtitles show up like on desktop.
   */
  private fun autoSelectSubTrackIfNeeded() {
    if (restoredTrackState || autoSubSelectedForThisVideo) return
    autoSubSelectedForThisVideo = true
    if ((MPVLib.getPropertyInt("sid") ?: 0) > 0) return
    val count = MPVLib.getPropertyInt("track-list/count") ?: 0
    val (firstSub, defaultSub) = scanSubTracks(count)
    val target = if (defaultSub != 0) defaultSub else firstSub
    if (target != 0) {
      MPVLib.setPropertyInt("sid", target)
      Log.d(TAG, "auto-selected subtitle track $target")
    }
  }

  private fun scanSubTracks(count: Int): Pair<Int, Int> {
    var firstSub = 0
    var defaultSub = 0
    for (i in 0 until count) {
      val isSub = MPVLib.getPropertyString("track-list/$i/type") == "sub"
      val id = if (isSub) MPVLib.getPropertyInt("track-list/$i/id") else null
      if (id != null && id > 0) {
        if (firstSub == 0) firstSub = id
        if (defaultSub == 0 && MPVLib.getPropertyString("track-list/$i/default") == "true") {
          defaultSub = id
        }
      }
    }
    return firstSub to defaultSub
  }

  /**
   * Resolves a content URI to the real video file on disk: direct document
   * paths first, then the provider's DATA column (MediaStore uris from file
   * managers and gallery apps have no document path at all).
   */
  private fun realVideoFileFromContentUri(uri: Uri?): File? {
    val documentFile = uri?.path
      ?.takeIf { it.startsWith("/document/primary:") }
      ?.removePrefix("/document/primary:")
      ?.let { File("/storage/emulated/0", it).takeIf { f -> f.isFile } }
    val dataFile = uri?.let { u ->
      runCatching {
        contentResolver.query(u, arrayOf(MediaStore.MediaColumns.DATA), null, null)
          ?.use { cursor -> cursor.takeIf { it.moveToFirst() }?.getString(0) }
      }.getOrNull()
    }?.takeIf { it.isNotBlank() }?.let { File(it).takeIf { f -> f.isFile } }
    return documentFile ?: dataFile
  }

  private fun realDirFromContentUri(uri: Uri?): File? {
    val relative = uri?.path
      ?.takeIf { it.startsWith("/document/primary:") }
      ?.removePrefix("/document/primary:")
      ?.substringBeforeLast('/', "")
      ?.takeIf { it.isNotBlank() }
      ?: return null
    val dirFile = File("/storage/emulated/0", relative)
    return dirFile.takeIf { it.isDirectory }
  }

  private fun guessSiblingSubtitle(video: File): File? {
    val base = video.nameWithoutExtension
    val exact = listOf("ass", "ssa", "srt").firstNotNullOfOrNull { extension ->
      File(video.parentFile, "$base.$extension").takeIf { it.isFile }
    }
    if (exact != null) return exact
    return video.parentFile
      ?.listFiles { file -> file.isFile && SUBTITLE_EXTENSIONS.matches(file.name) && file.name.startsWith(base) }
      ?.minByOrNull { it.name.length }
  }

  private fun parseAssFontNames(file: File): List<String> {
    val names = mutableSetOf<String>()
    var fontnameIndex = 1
    var inStylesSection = false
    file.useLines { lines ->
      for (raw in lines) {
        val line = raw.trim()
        if (line.startsWith("[")) {
          inStylesSection = line.equals("[V4+ Styles]", true) || line.equals("[V4 Styles]", true)
        } else if (inStylesSection && line.startsWith("Format:", true)) {
          fontnameIndex = line.substringAfter(':').split(',')
            .indexOfFirst { it.trim().equals("Fontname", true) }
            .coerceAtLeast(1)
        } else if (inStylesSection && line.startsWith("Style:", true)) {
          val fields = line.substringAfter(':').split(',')
          if (fields.size > fontnameIndex) names.add(fields[fontnameIndex].trim())
        }
      }
    }
    return names.toList()
  }

  /**
   * Runs before playback starts: parse the subtitle's style table and preload
   * every referenced family from the index, so the first render already has
   * them. Unresolved families are reported exactly once, after a single
   * incremental index refresh gets a chance to fill the gaps.
   */
  /** @return whether any referenced family is now available in the cache. */
  private suspend fun preloadSubtitleFonts(subtitlePath: String): Boolean {
    // With ASS styles forced off there is nothing to resolve: every subtitle
    // renders in the default font, so the video font pipeline stays out of
    // the way entirely.
    val requested = if (subtitlesPreferences.overrideAssSubs.get()) {
      emptyList()
    } else {
      runCatching { parseAssFontNames(File(subtitlePath)) }.getOrDefault(emptyList())
    }
    return if (requested.isEmpty()) false else stageAndReportFamilies(requested)
  }

  /**
   * Stages every family through the index; families the index cannot supply
   * get one incremental refresh + retry before being reported as missing.
   */
  private suspend fun stageAndReportFamilies(families: List<String>): Boolean {
    val destDir = fontsCacheDir()
    var stagedAny = false
    val missing = mutableListOf<String>()
    families.forEach { family ->
      if (attemptedFontFamilies.add(family)) {
        if (stageIndexedFont(family, destDir)) stagedAny = true else missing.add(family)
      }
    }
    if (missing.isNotEmpty() && !fontIndexer.indexIsEmpty()) {
      // Retry once against a refreshed index before blaming the library.
      refreshFontIndex()
      missing.forEach { family ->
        if (stageIndexedFont(family, destDir)) stagedAny = true else viewModel.reportMissingFont(family)
      }
    }
    return stagedAny
  }

  private suspend fun stageVideoFonts(videoPath: String?): Boolean {
    val destDir = fontsCacheDir()
    var stagedAny = ensureBundledFont(destDir)
    // The default font belongs to the typography feature, not to any video:
    // stage it silently so libass can resolve it (fallback font, or the only
    // font when ASS overriding is forced). It never reports missing.
    stagedAny = runCatching { stageIndexedFont(subtitlesPreferences.font.get(), destDir) }
      .getOrDefault(false) || stagedAny
    // No inline refreshFontIndex here: a full re-index takes minutes via SAF
    // and would stall this coroutine. The index is kept fresh by the
    // self-heal path in handleMissingFont and by the manual rebuild button.
    // Sibling fonts matter only for native ASS rendering: forced default-font
    // mode discards the script's styles and can never reference them.
    if (!subtitlesPreferences.overrideAssSubs.get()) {
      stagedAny = stageSiblingFonts(videoPath, destDir) || stagedAny
    }
    return stagedAny
  }

  /**
   * Called from the mpv log observer when libass cannot resolve a font family.
   * If the font exists in the index (large user folder or system fonts) it is
   * staged and the subtitle renderer reloads; otherwise the missing-font dialog
   * gets the name.
   */
  fun handleMissingFont(family: String) {
    // Forced default-font rendering makes per-style fonts irrelevant; keep
    // the typography feature and the video font pipeline isolated.
    if (subtitlesPreferences.overrideAssSubs.get()) return
    val trimmed = family.trim()
    if (trimmed.isEmpty() || !attemptedFontFamilies.add(trimmed)) return
    lifecycleScope.launch(Dispatchers.IO) {
      runCatching {
        var staged = stageIndexedFont(trimmed, fontsCacheDir())
        if (!staged && !fontRefreshOnMissDone) {
          fontRefreshOnMissDone = true
          refreshFontIndex(force = true)
          staged = stageIndexedFont(trimmed, fontsCacheDir())
        }
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
    runCatching {
      val folder = subtitlesPreferences.fontsFolder.get().takeIf { it.isNotBlank() }
      // an empty index (fresh install or post-wipe) must always rebuild
      val mustScan = folder != null && fontIndexer.indexIsEmpty()
      if (!mustScan && !force && now - subtitlesPreferences.fontIndexScanAt.get() < FONT_INDEX_SCAN_INTERVAL) {
        return
      }
      folder?.let { fontIndexer.reindexUserFolder(it) }
      if (subtitlesPreferences.useSystemFonts.get()) fontIndexer.reindexSystemFonts()
      subtitlesPreferences.fontIndexScanAt.set(now)
    }
  }

  private suspend fun stageSiblingFonts(videoPath: String?, destDir: File): Boolean {
    val parent = videoPath
      ?.takeUnless { it.startsWith("fd://") }
      ?.let { runCatching { File(it) }.getOrNull() }
      ?.takeIf { it.isFile }
      ?.parentFile
    val copiedAny = parent?.let { dir ->
      val fontDirs = sequenceOf(dir, File(dir, "fonts")).filter { it.isDirectory }
      fontDirs.fold(false) { any, folder -> copyFolderFonts(folder, destDir) || any }
    } ?: false
    return copiedAny
  }

  private fun copyFolderFonts(folder: File, destDir: File): Boolean {
    var any = false
    folder.listFiles { file -> file.isFile && FontIndexer.FONT_EXTENSIONS.matches(file.name) }
      ?.forEach { font ->
        val target = File(destDir, font.name)
        if (!target.exists() || target.length() != font.length()) {
          val copied = runCatching { font.copyTo(target, overwrite = true) }.isSuccess
          any = copied || any
        }
      }
    return any
  }

  private suspend fun stageIndexedFont(family: String, destDir: File): Boolean {
    val paths = fontIndexer.findFontPaths(family)
    if (paths.isEmpty()) return false
    var copied = false
    paths.forEach { path ->
      val targetName = path.substringAfterLast('/')
      val target = File(destDir, targetName)
      if (target.length() > 0) {
        // staged by an earlier session: the family is available
        copied = true
        return@forEach
      }
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
        folderUri?.let { fontIndexer.openIndexedFile(it, virtualPath) }
      }
    } ?: return false
    val copied = runCatching {
      source()?.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
      } != null
    }.getOrDefault(false)
    return copied && target.length() > 0
  }

  private fun ensureBundledFont(destDir: File): Boolean {
    val subfont = File(destDir, "subfont.ttf")
    if (subfont.exists()) return false
    runCatching {
      resources.assets.open("subfont.ttf").copyTo(subfont.outputStream())
    }
    return subfont.exists()
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

  private val mediaMimeTypes = setOf(
    "application/octet-stream",
    "application/x-matroska",
    "application/mp4",
    "application/ogg",
  )

  private fun isSupportedPlayable(intent: Intent): Boolean {
    val dataUri = intent.data ?: intent.getParcelableExtra(Intent.EXTRA_STREAM)
    val mime = intent.type
      ?: dataUri?.let { uri -> runCatching { contentResolver.getType(uri) }.getOrNull() }
    val name = runCatching { getFileName(intent) }.getOrNull()
      ?: dataUri?.lastPathSegment
      ?: intent.getStringExtra("uri")?.substringBefore('?')?.substringAfterLast('/')
    val extension = name?.substringBefore('?')?.substringAfterLast('.')?.lowercase()
    if (!extension.isNullOrEmpty() && extension != name) {
      return extension in videoExtensions || extension in audioExtensions ||
        extension in imageExtensions || mime.isMediaMime()
    }
    // No usable extension (opaque content IDs, extensionless links): only reject on a
    // clearly non-media mime, otherwise let mpv decide.
    return mime == null || mime.isMediaMime()
  }

  private fun String?.isMediaMime(): Boolean {
    if (this == null) return false
    return startsWith("video/") || startsWith("audio/") || startsWith("image/") || startsWith("text/") ||
      this in mediaMimeTypes
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
      val displayName = runCatching {
        contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null)?.use { cursor ->
          cursor.takeIf { it.moveToFirst() }?.getString(0)
        }
      }.getOrNull()
      if (displayName != null) return displayName
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

  private fun delayMillis(current: Double?, fallbackMillis: Int?): Int =
    ((current ?: fallbackMillis?.toDouble() ?: 0.0) * 1000).toInt()

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
          // mpv reports properties as unavailable while idle (e.g. saving the
          // old file's state right before loadfile); fall back to the stored
          // values instead of crashing on null.
          playbackSpeed = MPVLib.getPropertyDouble("speed") ?: oldState?.playbackSpeed ?: 1.0,
          sid = player.sid,
          subDelay = delayMillis(MPVLib.getPropertyDouble("sub-delay"), oldState?.subDelay),
          subSpeed = MPVLib.getPropertyDouble("sub-speed") ?: oldState?.subSpeed ?: 1.0,
          secondarySid = player.secondarySid,
          secondarySubDelay = delayMillis(
            MPVLib.getPropertyDouble("secondary-sub-delay"),
            oldState?.secondarySubDelay,
          ),
          aid = player.aid,
          audioDelay = delayMillis(MPVLib.getPropertyDouble("audio-delay"), oldState?.audioDelay),
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
      restoredTrackState = true
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
