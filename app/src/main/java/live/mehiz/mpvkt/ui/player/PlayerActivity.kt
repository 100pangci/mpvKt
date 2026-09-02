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
import android.graphics.Rect
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.database.entities.CustomButtonEntity
import live.mehiz.mpvkt.database.entities.PlaybackStateEntity
import live.mehiz.mpvkt.databinding.PlayerLayoutBinding
import live.mehiz.mpvkt.domain.playbackstate.repository.PlaybackStateRepository
import live.mehiz.mpvkt.network.NetworkSource
import live.mehiz.mpvkt.network.RemoteFontStager
import live.mehiz.mpvkt.player.FontConfigManager
import live.mehiz.mpvkt.player.FontIndexer
import live.mehiz.mpvkt.player.MPVLib
import live.mehiz.mpvkt.player.SubtitleFontPipeline
import live.mehiz.mpvkt.player.guessSiblingSubtitle
import live.mehiz.mpvkt.preferences.AdvancedPreferences
import live.mehiz.mpvkt.preferences.AudioPreferences
import live.mehiz.mpvkt.preferences.GesturePreferences
import live.mehiz.mpvkt.preferences.PlayerPreferences
import live.mehiz.mpvkt.preferences.SubtitlesPreferences
import live.mehiz.mpvkt.ui.player.controls.PlayerControls
import live.mehiz.mpvkt.ui.theme.MpvKtTheme
import org.koin.android.ext.android.inject
import java.io.File
import kotlin.math.roundToInt

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
  private val fontConfigManager: FontConfigManager by inject()
  private val json: Json by inject()
  private val remoteFontStager: RemoteFontStager by inject()
  private val intentResolver by lazy { IntentResolver(this) }
  private val fontPipeline by lazy {
    SubtitleFontPipeline(this, fontIndexer, subtitlesPreferences, lifecycleScope) {
      viewModel.reportMissingFont(it)
    }
  }

  private var restoredTrackState = false
  private var autoSubSelectedForThisVideo = false

  private var fileName = ""
  private var mediaPlaybackService: MediaPlaybackService? = null
  private var serviceBound = false

  /**
   * Playlist bookkeeping: [expectedIntentPath] is the entry the current
   * intent was supposed to play; mpv's native queue advance never goes
   * through onNewIntent, so a FILE_LOADED "path" mismatching it means the
   * player moved on by itself. [currentPlaybackPath] mirrors mpv's "path"
   * so per-episode state (and the watch history source) always refers to
   * the file that is actually playing.
   */
  private var expectedIntentPath: String? = null
  private var currentPlaybackPath: String? = null

  /**
   * Remote context of the currently playing media, from the launching
   * intent: the saved server config and the browsed directory. Queue
   * advances reuse them to re-stage that directory's fonts/ per episode.
   */
  private var currentRemoteSource: NetworkSource? = null
  private var currentRemoteDir: String? = null

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
    if (!intentResolver.isSupportedPlayable(intent)) {
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
        autoSelectTracksIfNeeded()
      }
    }
    setOrientation()

    binding.controls.setContent {
      MpvKtTheme {
        PlayerControls(
          viewModel = viewModel,
          onBackPress = ::finish,
          modifier = Modifier.onGloballyPositioned {
            val bounds = it.boundsInWindow()
            pipRect = Rect(
              bounds.left.roundToInt(),
              bounds.top.roundToInt(),
              bounds.right.roundToInt(),
              bounds.bottom.roundToInt(),
            )
          },
        )
      }
    }
  }

  /**
   * Resolves the video, finds its sibling subtitle and starts playback.
   *
   * Font setup runs concurrently with the loadfile: playback start waits a
   * short budget for it, so warm setups (fontconfig cache hit, fonts already
   * staged) finish before the subtitle renderer initializes and are picked
   * up with no reload at all. When the budget expires — a cold SAF index
   * rebuild can take minutes — the job keeps running and applies the late
   * fix (decoder recreation) once fonts have landed.
   */
  private suspend fun CoroutineScope.startPlaybackFlow(intent: Intent) {
    val playable = intentResolver.getPlayableUri(intent)
    Log.i(TAG, "playback flow: playable=$playable")
    val queue = intent.getStringArrayListExtra(QUEUE_EXTRA)
    if (!queue.isNullOrEmpty()) {
      startQueuePlaybackFlow(intent, queue, playable)
      return
    }
    if (playable == null) return
    val videoPath = resolveVideoContextPath(intent, playable)
    val siblingSubPath = siblingSubtitlePath(videoPath)
    Log.i(TAG, "playback flow: sibling=$siblingSubPath")
    awaitFontSetup(videoPath, siblingSubPath)
    withContext(Dispatchers.Main) {
      expectedIntentPath = playable
      if (isMpvIdleOrEmpty()) {
        // NOT player.playFile(): that only stores the path for the surface
        // callback to consume ONCE — if the surface already exists the file
        // would never load (black screen). Issue the command directly, exactly
        // like onNewIntent does; it is valid in any mpv state.
        MPVLib.command("loadfile", playable)
        siblingSubPath?.let { MPVLib.command("sub-add", it, "auto") }
      } else {
        // A second intent while playback runs must not tear down the queue:
        // append the new file instead of replacing the current entry.
        MPVLib.command("loadfile", playable, "append-play")
      }
    }
    Log.i(TAG, "playback flow: playFile issued")
  }

  /**
   * Builds mpv's native playlist from [queue] (raw, player-openable paths)
   * and starts at the entry the user tapped. Entries are appended in list
   * order first and only then jumped to, so the queue order stays identical
   * to the directory listing ("next" keeps following the listing).
   */
  private suspend fun CoroutineScope.startQueuePlaybackFlow(
    intent: Intent,
    queue: List<String>,
    playable: String?,
  ) {
    val startEntry = queue.firstOrNull { it == intent.dataString }
      ?: queue.firstOrNull { it == playable }
      ?: queue.first()
    val videoPath = resolveVideoContextPath(intent, startEntry)
    val siblingSubPath = siblingSubtitlePath(videoPath)
    Log.i(TAG, "playback flow: queue=${queue.size} start=$startEntry sibling=$siblingSubPath")
    awaitFontSetup(videoPath, siblingSubPath)
    withContext(Dispatchers.Main) {
      expectedIntentPath = startEntry
      if (isMpvIdleOrEmpty()) {
        queue.forEach { MPVLib.command("loadfile", it, "append") }
        val index = queue.indexOf(startEntry).coerceAtLeast(0)
        MPVLib.command("playlist-play-index", index.toString())
        Log.i(TAG, "playback flow: queue loaded, start index=$index")
      } else {
        // Playback already running: append the whole queue, don't disturb it.
        queue.forEach { MPVLib.command("loadfile", it, "append-play") }
        Log.i(TAG, "playback flow: queue appended while playing")
      }
    }
    Log.i(TAG, "playback flow: queue playFile issued")
  }

  /**
   * Where the media lives on disk for font/subtitle purposes: fd:// carries
   * no directory context, so fall back to the real file behind the content
   * URI (or its directory + file name).
   */
  private fun resolveVideoContextPath(intent: Intent, playablePath: String?): String? {
    playablePath?.takeUnless { it.startsWith("fd://") }?.let { return it }
    return intentResolver.realVideoFileFromContentUri(intent.data)?.absolutePath
      ?: intentResolver.realDirFromContentUri(intent.data)?.let { dir ->
        intentResolver.getFileName(intent).takeIf { it.isNotBlank() }
          ?.let { name -> File(dir, name).takeIf { f -> f.isFile } }?.absolutePath
      }
  }

  private fun siblingSubtitlePath(videoPath: String?): String? =
    videoPath?.let(::File)?.takeIf { it.isFile }?.let { guessSiblingSubtitle(it) }?.absolutePath

  private fun isMpvIdleOrEmpty(): Boolean =
    (MPVLib.getPropertyBoolean("idle-active") ?: true) || (MPVLib.getPropertyInt("playlist-count") ?: 0) == 0

  /**
   * Runs the font setup concurrently and waits a short budget for it, so a
   * warm setup applies before the subtitle renderer initializes while a cold
   * one keeps running and applies the late fix (decoder recreation) on its
   * own once fonts have landed.
   */
  private suspend fun CoroutineScope.awaitFontSetup(videoPath: String?, siblingSubPath: String?) {
    val fontSetupDone = CompletableDeferred<Boolean>()
    launch {
      runCatching {
        // mpv/libass re-read fonts.conf on every renderer initialization;
        // refresh it so the video's own directory is indexed in place.
        fontConfigManager.regenerate(videoPath)
        val stagedSub = siblingSubPath?.let { fontPipeline.preloadSubtitleFonts(it) } ?: false
        val stagedVideo = fontPipeline.stageVideoFonts()
        var staged = stagedSub || stagedVideo
        // Remote sources carry their own fonts/ folder: download it into the
        // subtitle font cache (sub-fonts-dir) before the renderer scans it.
        // A slow download outlives the loadfile budget on purpose; the late
        // reload applies it once done. The remote context is remembered for
        // queue advances, which bypass this flow entirely.
        intent.getStringExtra(REMOTE_SOURCE_EXTRA)?.let { remoteSourceJson ->
          intent.getStringExtra(REMOTE_PLAY_PATH_EXTRA)?.let { remoteDirPath ->
            val remoteSource = json.decodeFromString<NetworkSource>(remoteSourceJson)
            currentRemoteSource = remoteSource
            currentRemoteDir = remoteDirPath
            staged = remoteFontStager.stageFonts(remoteSource, remoteDirPath, fontPipeline.fontsCacheDir) || staged
          }
        }
        fontSetupDone.complete(staged)
        if (staged) {
          // Late fix for fonts that landed after the subtitle renderer
          // initialized: recreating the decoders rebuilds the renderer and
          // rescans every font source. A no-op while no track is selected.
          fontPipeline.reloadSubtitleRenderer()
        }
      }
      Log.i(TAG, "playback flow: font setup finished")
    }
    withTimeoutOrNull(FONT_SETUP_BUDGET_MS) { fontSetupDone.await() }
  }

  /**
   * mpv's native playlist advance never goes through startPlaybackFlow, so
   * the font setup would only ever run for the first episode. Re-run the
   * lightweight part on every queue-loaded file: regenerate fonts.conf for
   * the new file's directory, preload the sibling subtitle's families and
   * rebuild the subtitle renderer if fonts actually landed. Staged fonts
   * live in the shared fonts cache, so each episode's renderer
   * initialization picks them up even without the late reload.
   */
  private fun prepareFontsForQueueAdvance(path: String) {
    lifecycleScope.launch(Dispatchers.IO) {
      val videoFile = File(path).takeIf { it.isFile }
      if (videoFile == null) {
        stageRemoteFontsForQueueAdvance()
        return@launch
      }
      runCatching {
        fontConfigManager.regenerate(videoFile.absolutePath)
        val siblingSubPath = guessSiblingSubtitle(videoFile)?.absolutePath
        val stagedSub = siblingSubPath?.let { fontPipeline.preloadSubtitleFonts(it) } ?: false
        val stagedVideo = fontPipeline.stageVideoFonts()
        if (stagedSub || stagedVideo) {
          fontPipeline.reloadSubtitleRenderer()
        }
      }
      Log.i(TAG, "playback flow: queue advance font setup finished")
    }
  }

  /**
   * Queue advance on a network source: mpv moved to another remote URL on
   * its own, so re-stage the browsed directory's fonts/ (they differ per
   * episode) and reload the renderer. Same caps and swallow-all-failures
   * policy as the first episode; there is no local sibling subtitle to
   * preload by design (remote .ass files are not auto-loaded yet).
   */
  private fun stageRemoteFontsForQueueAdvance() {
    val source = currentRemoteSource
    val dir = currentRemoteDir
    if (source == null || dir == null) {
      Log.i(TAG, "playback flow: queue advance is not a local file, skipping font setup")
      return
    }
    val staged = runCatching {
      remoteFontStager.stageFonts(source, dir, fontPipeline.fontsCacheDir)
    }.onFailure { Log.w(TAG, "queue advance remote font staging failed: ${it.message}") }
      .getOrDefault(false)
    if (staged) fontPipeline.reloadSubtitleRenderer()
    Log.i(TAG, "playback flow: queue advance font setup finished")
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
      enterPipMode()
    }
    super.onUserLeaveHint()
  }

  @Deprecated("Deprecated in Java")
  @Suppress("DEPRECATION")
  @SuppressLint("NewApi")
  override fun onBackPressed() {
    if (isPipSupported && viewModel.paused == false && playerPreferences.automaticallyEnterPip.get()) {
      if (viewModel.sheetShown.value == Sheets.None && viewModel.panelShown.value == Panels.None) {
        enterPipMode()
      }
    } else {
      super.onBackPressed()
    }
  }

  internal fun enterPipMode() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      enterPictureInPictureMode(createPipParams())
    } else {
      @Suppress("DEPRECATION")
      enterPictureInPictureMode()
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
    applyLegacyImmersiveFlags()
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

  @Suppress("DEPRECATION")
  private fun applyLegacyImmersiveFlags() {
    binding.root.systemUiVisibility =
      View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
      View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
      View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
      View.SYSTEM_UI_FLAG_LOW_PROFILE
  }

  private fun setupMPV() {
    copyMPVAssets()
    requestStoragePermission()
    player.initialize(filesDir.path, cacheDir.path)
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
        fontPipeline.handleMissingFont(family)
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

  fun stageSubFont(family: String) {
    fontPipeline.stageSubFont(family)
  }

  /**
   * Deterministic track selection for freshly opened files (skipped when a
   * per-video track state was restored from the database):
   * - audio: the first audio track
   * - subtitle: a track matching the user's preferred languages, then
   *   embedded tracks, then external ones
   */
  private fun autoSelectTracksIfNeeded() {
    val (audioIds, subTracks) = collectTracks()
    // Observing the property emits an initial event with an empty track list
    // while idle; don't consume the one-shot attempt on it — wait for the
    // file's real track list.
    val tracksNotLoadedYet = audioIds.isEmpty() && subTracks.isEmpty()
    if (restoredTrackState || autoSubSelectedForThisVideo || tracksNotLoadedYet) return
    autoSubSelectedForThisVideo = true

    if (audioIds.isNotEmpty()) {
      MPVLib.setPropertyInt("aid", audioIds.first())
      Log.d(TAG, "auto-selected first audio track ${audioIds.first()}")
    }

    if ((MPVLib.getPropertyInt("sid") ?: 0) > 0) return
    val preferred = subtitlesPreferences.preferredLanguages.get().split(',').filter { it.isNotBlank() }
    val target = subTracks.firstOrNull { matchesPreferredLanguages(it.lang, preferred) }
      ?: subTracks.firstOrNull { !it.external }
      ?: subTracks.firstOrNull { it.external }
    if (target != null) {
      MPVLib.setPropertyInt("sid", target.id)
      Log.d(TAG, "auto-selected subtitle track ${target.id} (external=${target.external})")
    }
  }

  private fun collectTracks(): Pair<List<Int>, List<SubTrack>> {
    val count = MPVLib.getPropertyInt("track-list/count") ?: 0
    val audioIds = mutableListOf<Int>()
    val subTracks = mutableListOf<SubTrack>()
    for (i in 0 until count) {
      val id = MPVLib.getPropertyInt("track-list/$i/id") ?: continue
      when (MPVLib.getPropertyString("track-list/$i/type")) {
        "audio" -> audioIds += id
        "sub" -> subTracks += SubTrack(
          id,
          MPVLib.getPropertyString("track-list/$i/lang"),
          MPVLib.getPropertyString("track-list/$i/external") == "true",
        )
      }
    }
    return audioIds to subTracks
  }

  private fun matchesPreferredLanguages(lang: String?, preferred: List<String>): Boolean {
    if (lang.isNullOrBlank()) return false
    val normalized = lang.trim().lowercase()
    return preferred.any { token ->
      val t = token.trim().lowercase()
      t.isNotEmpty() && (normalized == t || normalized.startsWith(t) || t.startsWith(normalized))
    }
  }

  private data class SubTrack(val id: Int, val lang: String?, val external: Boolean)

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

  private fun rejectUnsupportedFile() {
    Toast.makeText(this, R.string.error_unsupported_file, Toast.LENGTH_LONG).show()
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
      // Only end the activity when the queue has nothing left to play:
      // mpv reports eof-reached for a moment between queue entries, too.
      "eof-reached" if value && playerPreferences.closeAfterReachingEndOfVideo.get() &&
        (MPVLib.getPropertyInt("playlist-pos") ?: 0) >= (MPVLib.getPropertyInt("playlist-count") ?: 1) - 1 ->
        finishAndRemoveTask()
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
        val mpvPath = MPVLib.getPropertyString("path")
        // mpv advancing to the next queue entry bypasses onNewIntent: when
        // the loaded path differs from the one the current intent was meant
        // to play, this is an automatic queue advance.
        val isQueueAdvance = mpvPath != null && mpvPath != expectedIntentPath
        currentPlaybackPath = mpvPath
        fileName = if (isQueueAdvance) {
          mpvPath!!.substringAfterLast('/').ifBlank { intentResolver.getFileName(intent) }
        } else {
          intentResolver.getFileName(intent)
        }
        if (isQueueAdvance) {
          // The intent's subtitle/position extras belong to the first file;
          // reapplying them would add stale subtitle tracks every episode.
          // A leftover force-media-title would also leak onto every next file.
          MPVLib.setPropertyString("force-media-title", "")
        } else {
          setIntentExtras(intent.extras)
        }
        // Track choices are per video: a previous file's restore must not
        // block the current file's deterministic selection.
        restoredTrackState = false
        autoSubSelectedForThisVideo = false
        val mediaTitle = MPVLib.getPropertyString("media-title")
        if (mediaTitle.isNullOrBlank() || mediaTitle.isDigitsOnly()) {
          MPVLib.setPropertyString("media-title", fileName)
        }
        lifecycleScope.launch(Dispatchers.IO) {
          loadVideoPlaybackState(fileName)
        }
        if (isQueueAdvance) {
          mpvPath?.let(::prepareFontsForQueueAdvance)
        }
        setOrientation()
        viewModel.changeVideoAspect(playerPreferences.videoAspect.get())
      }

      MPVLib.mpvEventId.MPV_EVENT_PLAYBACK_RESTART -> player.isExiting = false
    }
  }

  private fun delayMillis(current: Double?, fallbackMillis: Int?): Int =
    ((current ?: fallbackMillis?.toDouble() ?: 0.0) * 1000).toInt()

  /**
   * Persists a track id only when it is actually known: mpv reports "auto"
   * (unreadable as a number) for tracks it has not resolved yet, e.g. while
   * idle. Falls back to the previously saved id, then the deterministic
   * default (first audio track, no subtitles).
   */
  private fun resolveTrackId(current: Int, saved: Int?, default: Int): Int =
    current.takeIf { it >= 0 } ?: saved?.takeIf { it >= 0 } ?: default

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
          // values instead of crashing on null. Track ids of -1 ("unknown")
          // must not persist either: restoring them would deselect the track.
          playbackSpeed = MPVLib.getPropertyDouble("speed") ?: oldState?.playbackSpeed ?: 1.0,
          sid = resolveTrackId(player.sid, oldState?.sid, default = 0),
          subDelay = delayMillis(MPVLib.getPropertyDouble("sub-delay"), oldState?.subDelay),
          subSpeed = MPVLib.getPropertyDouble("sub-speed") ?: oldState?.subSpeed ?: 1.0,
          secondarySid = resolveTrackId(player.secondarySid, oldState?.secondarySid, default = 0),
          secondarySubDelay = delayMillis(
            MPVLib.getPropertyDouble("secondary-sub-delay"),
            oldState?.secondarySubDelay,
          ),
          aid = resolveTrackId(player.aid, oldState?.aid, default = 1),
          audioDelay = delayMillis(MPVLib.getPropertyDouble("audio-delay"), oldState?.audioDelay),
          duration = viewModel.duration ?: oldState?.duration ?: 0,
          lastPlayedAt = System.currentTimeMillis(),
          uri = historyUriFor(oldState),
        ),
      )
    }
  }

  /**
   * Source the currently playing file came from: mpv's own "path" is the
   * most accurate one (queue advances bypass the intent); fd:// handles are
   * ephemeral, so fall back to the raw intent source and the previously
   * stored value.
   */
  private fun historyUriFor(oldState: PlaybackStateEntity?): String =
    currentPlaybackPath?.takeUnless { it.startsWith("fd://") }
      ?: resolveHistoryUri()
      ?: oldState?.uri
      ?: ""

  /**
   * Remembers where the current media came from so the watch history can
   * resume it later: the raw intent data covers VIEW and SEND (stream)
   * launches, the text extra covers shared URLs. Ephemeral handles are
   * never stored; resuming re-runs the full intent resolution instead.
   */
  private fun resolveHistoryUri(): String? {
    @Suppress("DEPRECATION")
    val uri = intent.data ?: intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
    return uri?.toString() ?: intent.getStringExtra(Intent.EXTRA_TEXT)
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
    // off, so only restore once the video was actually watched. Legacy rows
    // may hold -1 ("unknown") track ids: skip those instead of writing "no".
    state?.takeIf { it.lastPosition > 0 }?.let {
      restoredTrackState = true
      if (it.sid >= 0) player.sid = it.sid
      if (it.secondarySid >= 0) player.secondarySid = it.secondarySid
      if (it.aid >= 0) player.aid = it.aid
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
    if (!intentResolver.isSupportedPlayable(intent)) {
      rejectUnsupportedFile()
      return
    }

    setIntent(intent)
    // Route through the same flow as onCreate: font setup and sibling
    // subtitle handling must also run for consecutive files.
    lifecycleScope.launch(Dispatchers.IO) {
      startPlaybackFlow(intent)
    }
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

    // extras of queue playback: the full playlist in playback order (raw
    // paths or URLs), the intent data being the entry to start at
    const val QUEUE_EXTRA = "queue"

    // extras of remote-source playback: the serialized NetworkSource and the
    // remote directory the video lives in (its fonts/ folder gets staged)
    const val REMOTE_SOURCE_EXTRA = "remote-source"
    const val REMOTE_PLAY_PATH_EXTRA = "remote-play-path"

    @Volatile
    var lastMpvError: String? = null
  }
}

const val TAG = "mpvKt"
