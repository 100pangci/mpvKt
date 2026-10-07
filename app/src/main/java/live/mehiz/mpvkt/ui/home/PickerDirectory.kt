package live.mehiz.mpvkt.ui.home

import android.net.Uri
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.AbstractFile

internal fun FileManager.resolvePickerDirectory(uri: Uri): AbstractFile? = try {
  fromUri(uri)?.takeIf { isDirectory(it) }
} catch (_: SecurityException) {
  // SAF permissions may have expired or been revoked since navigation was saved.
  null
} catch (_: IllegalArgumentException) {
  // The document provider or document ID may no longer exist.
  null
}
