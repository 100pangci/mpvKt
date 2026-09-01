package live.mehiz.mpvkt.player

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import live.mehiz.mpvkt.preferences.SubtitlesPreferences
import live.mehiz.mpvkt.ui.player.FONT_INDEX_SCAN_INTERVAL
import live.mehiz.mpvkt.ui.player.SUBTITLE_EXTENSIONS
import java.io.File

/**
 * Parses the [V4(+)] Styles table of an ASS/SSA script and returns every
 * family referenced by a Style line. Pure file logic; unit-tested.
 */
internal fun parseAssFontNames(file: File): List<String> {
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

internal fun guessSiblingSubtitle(video: File): File? {
  val base = video.nameWithoutExtension
  val exact = listOf("ass", "ssa", "srt").firstNotNullOfOrNull { extension ->
    File(video.parentFile, "$base.$extension").takeIf { it.isFile }
  }
  if (exact != null) return exact
  return video.parentFile
    ?.listFiles { file -> file.isFile && SUBTITLE_EXTENSIONS.matches(file.name) && file.name.startsWith(base) }
    ?.minByOrNull { it.name.length }
}

/**
 * Owns everything font-related during playback. The primary path is mpv's
 * fontconfig provider ([FontConfigManager] indexes font directories in
 * place); this pipeline layers the index-based copy fallback on top for
 * sources fontconfig cannot see (SAF-only folders), preloads the sibling
 * subtitle's families and runs the missing-font self-heal. The only state
 * shared with the player is the missing-font report callback.
 */
@Suppress("TooManyFunctions")
internal class SubtitleFontPipeline(
  private val context: Context,
  private val fontIndexer: FontIndexer,
  private val subtitlesPreferences: SubtitlesPreferences,
  private val scope: CoroutineScope,
  private val reportMissingFont: (String) -> Unit,
) {

  /**
   * Families fully handled this session (staged successfully, or attempted
   * and reported by the self-heal). Guarding [handleMissingFont] with it
   * prevents re-triggering on every log line libass emits for the same
   * family; preloading deliberately does NOT write into it, so the self-heal
   * always gets its one retry with a refreshed index.
   */
  private val attemptedFontFamilies = mutableSetOf<String>()

  val fontsCacheDir: File get() = File(context.cacheDir, "fonts").apply { mkdirs() }

  /**
   * Called from the mpv log observer when libass cannot resolve a font family.
   * If the font exists in the index it is staged and the subtitle renderer
   * reloads; otherwise the missing-font dialog gets the name.
   */
  fun handleMissingFont(family: String) {
    // Forced default-font rendering makes per-style fonts irrelevant; keep
    // the typography feature and the video font pipeline isolated.
    if (subtitlesPreferences.overrideAssSubs.get()) return
    val trimmed = family.trim()
    if (trimmed.isEmpty() || !attemptedFontFamilies.add(trimmed)) return
    scope.launch(Dispatchers.IO) {
      runCatching {
        var staged = stageIndexedFont(trimmed, fontsCacheDir)
        if (!staged) {
          refreshFontIndex(force = true)
          staged = stageIndexedFont(trimmed, fontsCacheDir)
        }
        if (staged) {
          reloadSubtitleRenderer()
        } else {
          reportMissingFont(trimmed)
        }
      }
    }
  }

  /**
   * Stages the family picked in the typography card (used as the default
   * subtitle font) and applies it immediately. fontconfig usually resolves
   * the family in place; the staged copy is a fallback for SAF-only folders.
   */
  fun stageSubFont(family: String) {
    scope.launch(Dispatchers.IO) {
      runCatching {
        ensureBundledFont(fontsCacheDir)
        // The family comes straight from the index list: an incremental
        // refresh (throttled) is enough, no forced full scan per pick.
        refreshFontIndex()
        stageIndexedFont(family, fontsCacheDir)
        MPVLib.setPropertyString("sub-font", family)
        reloadSubtitleRenderer()
      }
    }
  }

  /** @return whether any referenced family is now available in the cache. */
  suspend fun preloadSubtitleFonts(subtitlePath: String): Boolean {
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

  suspend fun stageVideoFonts(videoPath: String?): Boolean {
    val destDir = fontsCacheDir
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
   * Stages every family through the index; families the index cannot supply
   * get one incremental refresh + retry before being reported as missing.
   * Unresolved families stay unmarked so the log-driven self-heal can still
   * pick them up once the index actually contains them.
   */
  private suspend fun stageAndReportFamilies(families: List<String>): Boolean {
    val destDir = fontsCacheDir
    var stagedAny = false
    val missing = mutableListOf<String>()
    families.forEach { family ->
      if (family in attemptedFontFamilies) return@forEach
      if (stageIndexedFont(family, destDir)) {
        attemptedFontFamilies.add(family)
        stagedAny = true
      } else {
        missing.add(family)
      }
    }
    if (missing.isNotEmpty()) {
      // Retry once against a refreshed index before blaming the library.
      refreshFontIndex()
      missing.forEach { family ->
        if (stageIndexedFont(family, destDir)) {
          attemptedFontFamilies.add(family)
          stagedAny = true
        } else {
          reportMissingFont(family)
        }
      }
    }
    return stagedAny
  }

  private suspend fun refreshFontIndex(force: Boolean = false) {
    val now = System.currentTimeMillis()
    runCatching {
      val folder = subtitlesPreferences.fontsFolder.get().takeIf { it.isNotBlank() }
      // An empty index (fresh install or post-wipe) must always rebuild,
      // regardless of where the fonts are expected to come from.
      val mustScan = fontIndexer.indexIsEmpty()
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
    val entries = fontIndexer.findFontEntries(family)
    if (entries.isEmpty()) return false
    var copied = false
    entries.distinctBy { it.path }.forEach { entry ->
      val targetName = entry.path.substringAfterLast('/')
      val target = File(destDir, targetName)
      if (target.length() > 0 && target.length() == entry.size) {
        // staged by an earlier session: the family is available
        copied = true
        return@forEach
      }
      copied = copied or copyIndexedFile(entry.path, entry.size, target)
    }
    return copied
  }

  private suspend fun copyIndexedFile(virtualPath: String, expectedSize: Long, target: File): Boolean {
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
    // A truncated or interrupted copy must never count as staged, or a
    // corrupt font file poisons the cache directory forever.
    return copied && target.length() > 0 && (expectedSize <= 0L || target.length() == expectedSize)
  }

  private fun ensureBundledFont(destDir: File): Boolean {
    val subfont = File(destDir, "subfont.ttf")
    if (subfont.exists()) return false
    runCatching {
      context.resources.assets.open("subfont.ttf").copyTo(subfont.outputStream())
    }
    return subfont.exists()
  }

  /**
   * Forces mpv to recreate the subtitle decoders so the ASS renderer (and
   * with it the fontconfig provider and the fonts directory scan) is rebuilt.
   * `sub-reload` only works on external tracks; re-selecting the track
   * uniformly covers embedded ones as well.
   */
  fun reloadSubtitleRenderer() {
    runCatching {
      val sid = MPVLib.getPropertyInt("sid")
      if (sid != null && sid > 0) {
        MPVLib.setPropertyString("sid", "no")
        MPVLib.setPropertyInt("sid", sid)
      }
      val secondarySid = MPVLib.getPropertyInt("secondary-sid")
      if (secondarySid != null && secondarySid > 0) {
        MPVLib.setPropertyString("secondary-sid", "no")
        MPVLib.setPropertyInt("secondary-sid", secondarySid)
      }
    }
  }
}
