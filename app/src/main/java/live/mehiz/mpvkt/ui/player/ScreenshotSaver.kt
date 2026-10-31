package live.mehiz.mpvkt.ui.player

import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.player.MPVLib
import live.mehiz.mpvkt.preferences.PlayerPreferences
import live.mehiz.mpvkt.ui.utils.canAccessAllFiles
import live.mehiz.mpvkt.ui.utils.treeUriToRealPath
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Saves mpv screenshots. A SAF folder picked by the user wins: on Android 11+
 * writing into shared storage needs "All files access", which the app should
 * not demand just to take screenshots. mpv can only write to a real path, so
 * SAF captures are rendered into the cache first and copied into the tree.
 */
@Suppress("TooManyFunctions")
internal class ScreenshotSaver(
  private val activity: PlayerActivity,
  private val preferences: PlayerPreferences,
) {
  private var counter = 0

  /** True when a screenshot can be taken without asking for a folder first. */
  fun hasDestination(): Boolean =
    preferences.screenshotTreeUri.get().isNotBlank() || activity.canAccessAllFiles()

  suspend fun capture(withSubtitles: Boolean, showMessage: (String) -> Unit) {
    val treeUri = preferences.screenshotTreeUri.get().takeIf { it.isNotBlank() }
    if (treeUri != null) {
      captureToTree(Uri.parse(treeUri), withSubtitles, showMessage)
    } else {
      captureToDirectory(withSubtitles, showMessage)
    }
  }

  private suspend fun captureToDirectory(withSubtitles: Boolean, showMessage: (String) -> Unit) {
    val directory = resolveDirectory()
    directory.mkdirs()
    val file = nextFile(directory)
    if (!captureTo(file, withSubtitles)) {
      showMessage(failureMessage(directory))
      return
    }
    // Direct file writes are not indexed by MediaProvider on its own.
    MediaScannerConnection.scanFile(activity, arrayOf(file.absolutePath), arrayOf("image/png"), null)
    showMessage(activity.getString(R.string.screenshot_saved, file.name))
  }

  private suspend fun captureToTree(
    treeUri: Uri,
    withSubtitles: Boolean,
    showMessage: (String) -> Unit,
  ) {
    val cacheDirectory = File(activity.cacheDir, CACHE_DIRECTORY).apply { mkdirs() }
    val temporary = nextFile(cacheDirectory)
    try {
      if (!captureTo(temporary, withSubtitles)) {
        showMessage(failureMessage(cacheDirectory))
        return
      }
      val savedName = runCatching { copyToTree(treeUri, temporary) }
        .getOrElse { error ->
          Log.w(TAG, "screenshot copy into $treeUri failed", error)
          if (error is SecurityException) {
            requestFolder(withSubtitles)
          } else {
            showMessage(failureMessage(cacheDirectory, error.message))
          }
          return
        }
      if (savedName == null) {
        // The tree is gone or the persisted grant was revoked.
        requestFolder(withSubtitles)
      } else {
        scanTreeFile(treeUri, savedName)
        showMessage(activity.getString(R.string.screenshot_saved, savedName))
      }
    } finally {
      temporary.delete()
    }
  }

  private suspend fun captureTo(file: File, withSubtitles: Boolean): Boolean {
    PlayerActivity.lastMpvError = null
    MPVLib.command(
      "screenshot-to-file",
      file.absolutePath,
      if (withSubtitles) "subtitles" else "video",
    )
    return awaitFile(file)
  }

  private fun resolveDirectory(): File = File(
    preferences.screenshotDirectory.get().ifBlank {
      File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
        "mpvKt",
      ).path
    },
  )

  private fun nextFile(directory: File): File {
    val timeText = MPVLib.getPropertyDouble("time-pos")
      ?.let { formatTimestamp(it) }
      ?: "unknown"
    val videoName = activity.fileName.substringBeforeLast('.').ifBlank { "video" }
    var index = ++counter
    var file = File(directory, "$videoName-$timeText-N${index.toString().padStart(4, '0')}.png")
    while (file.exists()) {
      index = ++counter
      file = File(directory, "$videoName-$timeText-N${index.toString().padStart(4, '0')}.png")
    }
    return file
  }

  /**
   * Waits until the file exists and its size stopped growing, so a large PNG
   * is never copied to the tree half-written.
   */
  private suspend fun awaitFile(file: File): Boolean {
    var previousSize = -1L
    var waited = 0
    while (waited < 3000) {
      delay(200)
      waited += 200
      val size = if (file.exists()) file.length() else 0L
      if (size > 0L && size == previousSize) return true
      previousSize = size
    }
    return previousSize > 0L
  }

  /**
   * Copies [source] into the picked tree. Returns the final display name, or
   * null when the tree itself is no longer usable (revoked grant, deleted
   * folder) and the user has to pick a folder again.
   */
  private fun copyToTree(treeUri: Uri, source: File): String? {
    val tree = DocumentFile.fromTreeUri(activity, treeUri)
      ?.takeIf { it.exists() && it.canWrite() }
    val target = tree?.let { createUniqueDocument(it, source.name) }
    if (target == null) return null
    val written = activity.contentResolver.openOutputStream(target.uri)?.use { output ->
      source.inputStream().use { input -> input.copyTo(output) }
    } != null
    if (!written) {
      target.delete()
      throw IOException("Cannot write screenshot")
    }
    return target.name ?: source.name
  }

  private fun createUniqueDocument(tree: DocumentFile, name: String): DocumentFile? {
    val base = name.substringBeforeLast('.')
    val extension = name.substringAfterLast('.', "png")
    var candidate = name
    var index = 1
    while (tree.findFile(candidate) != null) {
      candidate = "$base-${index++}.$extension"
    }
    return tree.createFile("image/png", candidate)
  }

  private fun scanTreeFile(treeUri: Uri, name: String) {
    val directory = treeUriToRealPath(treeUri) ?: return
    runCatching {
      MediaScannerConnection.scanFile(
        activity,
        arrayOf(File(directory, name).absolutePath),
        arrayOf("image/png"),
        null,
      )
    }
  }

  private suspend fun requestFolder(withSubtitles: Boolean) {
    // The stored grant is unusable; clear it so the picker result is authoritative.
    preferences.screenshotTreeUri.set("")
    withContext(Dispatchers.Main) { activity.requestScreenshotFolder(withSubtitles) }
  }

  private fun failureMessage(directory: File, detail: String? = null): String {
    val appCanWrite = isDirectoryWritable(directory)
    Log.d(TAG, screenshotDiagnostic(directory, appCanWrite, detail))
    val reason = when {
      !directory.exists() -> activity.getString(R.string.screenshot_dir_missing)
      !appCanWrite -> activity.getString(R.string.screenshot_dir_unwritable)
      detail != null -> detail
      PlayerActivity.lastMpvError != null -> PlayerActivity.lastMpvError
      else -> activity.getString(R.string.screenshot_error_unknown)
    }
    return activity.getString(R.string.screenshot_failed) + ": " + reason
  }

  private fun isDirectoryWritable(directory: File): Boolean = try {
    val probe = File(directory, ".mpvkt_probe")
    probe.writeText("t")
    probe.delete()
    true
  } catch (_: Exception) {
    false
  }

  private fun screenshotDiagnostic(directory: File, appCanWrite: Boolean, detail: String?): String =
    "screenshot FAILED: dir=${directory.absolutePath} dirExists=${directory.exists()} " +
      "appCanWrite=$appCanWrite mpvError=${PlayerActivity.lastMpvError} detail=$detail"

  private fun formatTimestamp(seconds: Double): String {
    val totalMs = (seconds * 1000).roundToInt()
    val hours = totalMs / 3_600_000
    val minutes = (totalMs / 60_000) % 60
    val seconds = (totalMs / 1_000) % 60
    val millis = totalMs % 1_000
    return String.format(Locale.US, "%02d-%02d-%02d.%03d", hours, minutes, seconds, millis)
  }

  private companion object {
    const val CACHE_DIRECTORY = "screenshots"
  }
}
