package live.mehiz.mpvkt.ui.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toUri
import java.io.File

internal val mediaMimeTypes = setOf(
  "application/octet-stream",
  "application/x-matroska",
  "application/mp4",
  "application/ogg",
)

internal fun String?.isMediaMime(): Boolean {
  if (this == null) return false
  return startsWith("video/") || startsWith("audio/") || startsWith("image/") || startsWith("text/") ||
    this in mediaMimeTypes
}

/**
 * Resolves [Intent]s into something mpv can open: real paths, fd:// handles
 * or network uris. Stateless; the context is only used for content resolver
 * queries.
 */
internal class IntentResolver(private val context: Context) {

  fun getPlayableUri(intent: Intent): String? {
    val data = intent.data
    val fromFd = data?.takeIf { it.scheme == "content" }?.let { uri ->
      runCatching { uri.openContentFd(context) }.getOrNull()
        // The provider denied access (no grant, revoked permission, offline
        // cloud): with All-Files-Access the file is still reachable directly.
        ?: contentUriToRealFile(uri)?.absolutePath
    }
    return fromFd ?: parsePathFromIntent(intent)?.let { uri ->
      if (uri.startsWith("content://")) uri.toUri().openContentFd(context) else uri
    }
  }

  @Suppress("NestedBlockDepth")
  fun parsePathFromIntent(intent: Intent): String? {
    return when (intent.action) {
      Intent.ACTION_VIEW -> intent.data?.resolveUri(context)
      Intent.ACTION_SEND -> {
        if (intent.hasExtra(Intent.EXTRA_STREAM)) {
          @Suppress("DEPRECATION")
          intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.resolveUri(context)
        } else {
          intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
            val uri = it.trim().toUri()
            if (uri.isHierarchical && !uri.isRelative) uri.resolveUri(context) else null
          }
        }
      }

      else -> intent.getStringExtra("uri")
    }
  }

  fun getFileName(intent: Intent): String {
    val uri = if (intent.type == "text/plain") {
      intent.getStringExtra(Intent.EXTRA_TEXT)!!.toUri()
    } else {
      @Suppress("DEPRECATION")
      (intent.data ?: intent.getParcelableExtra(Intent.EXTRA_STREAM))
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && uri != null) {
      val displayName = runCatching {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null)?.use { cursor ->
          cursor.takeIf { it.moveToFirst() }?.getString(0)
        }
      }.getOrNull()
      if (displayName != null) return displayName
    }
    return uri?.lastPathSegment?.substringAfterLast("/") ?: uri?.path ?: ""
  }

  fun isSupportedPlayable(intent: Intent): Boolean {
    @Suppress("DEPRECATION")
    val dataUri = intent.data ?: intent.getParcelableExtra(Intent.EXTRA_STREAM)
    val mime = intent.type
      ?: dataUri?.let { uri -> runCatching { context.contentResolver.getType(uri) }.getOrNull() }
    val name = runCatching { getFileName(intent) }.getOrNull()
      ?: dataUri?.lastPathSegment
      ?: intent.getStringExtra("uri")?.substringBefore('?')?.substringAfterLast('/')
    val extension = name?.substringBefore('?')?.substringAfterLast('.')?.lowercase()
    if (!extension.isNullOrEmpty() && extension != name) {
      return extension in videoExtensions || extension in audioExtensions ||
        extension in imageExtensions || mime.isMediaMime()
    }
    return mime == null || mime.isMediaMime()
  }

  /**
   * Resolves a content URI to the real video file on disk: direct document
   * paths first, then the provider's DATA column (MediaStore uris from file
   * managers and gallery apps have no document path at all).
   */
  fun realVideoFileFromContentUri(uri: Uri?): File? {
    val documentFile = uri?.path
      ?.takeIf { it.startsWith("/document/primary:") }
      ?.removePrefix("/document/primary:")
      ?.let { File("/storage/emulated/0", it).takeIf { f -> f.isFile } }
    val dataFile = uri?.let { u ->
      runCatching {
        context.contentResolver.query(u, arrayOf(MediaStore.MediaColumns.DATA), null, null)
          ?.use { cursor -> cursor.takeIf { it.moveToFirst() }?.getString(0) }
      }.getOrNull()
    }?.takeIf { it.isNotBlank() }?.let { File(it).takeIf { f -> f.isFile } }
    return documentFile ?: dataFile
  }

  fun realDirFromContentUri(uri: Uri?): File? {
    val relative = uri?.path
      ?.takeIf { it.startsWith("/document/primary:") }
      ?.removePrefix("/document/primary:")
      ?.substringBeforeLast('/', "")
      ?.takeIf { it.isNotBlank() }
      ?: return null
    val dirFile = File("/storage/emulated/0", relative)
    return dirFile.takeIf { it.isDirectory }
  }

  private fun contentUriToRealFile(uri: Uri): File? {
    val relative = uri.path
      ?.takeIf { it.startsWith("/document/primary:") }
      ?.removePrefix("/document/primary:")
      ?: return null
    return File("/storage/emulated/0", relative).takeIf { it.isFile }
  }
}
