package live.mehiz.mpvkt.ui.utils

import android.net.Uri
import android.os.Environment

/**
 * Maps an external-storage document tree URI back to its real filesystem
 * path. Returns null for providers that expose no path at all (cloud
 * providers, MediaStore, ...); those folders are still usable through SAF,
 * only path-based features such as the media scanner have to skip them.
 */
fun treeUriToRealPath(uri: Uri): String? {
  if (uri.authority != "com.android.externalstorage.documents") return null
  val treeId = uri.toString()
    .substringBefore('#')
    .substringBefore('?')
    .substringAfter("/tree/", "")
    .let { Uri.decode(it) }
  val volume = treeId.substringBefore(':', "")
  val rest = treeId.substringAfter(':', "")
  val base = when (volume) {
    "primary" -> Environment.getExternalStorageDirectory().absolutePath
    else -> "/storage/$volume"
  }
  return when {
    volume.isBlank() || base.isBlank() -> null
    rest.isBlank() -> base
    else -> "$base/$rest"
  }
}

/** Human-readable folder label for a document tree URI, e.g. "Pictures/mpvKt". */
fun treeUriLabel(uri: Uri): String {
  val treeId = uri.toString()
    .substringBefore('#')
    .substringBefore('?')
    .substringAfter("/tree/", "")
    .let { Uri.decode(it) }
  return treeId.substringAfter(':', treeId).ifBlank { treeId }.ifBlank { uri.toString() }
}
