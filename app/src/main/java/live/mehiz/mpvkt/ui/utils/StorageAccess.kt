package live.mehiz.mpvkt.ui.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

/**
 * "All files access" (Android 11+) has no runtime dialog: the grant happens
 * on a system settings page. These helpers are shared by the home banner and
 * the in-player prompt so both check and request the same permission.
 */

/**
 * True when the app can read arbitrary paths on shared storage — required to
 * discover sibling subtitles/fonts for files opened from other apps, since a
 * SAF file grant covers only the single file, never its directory.
 */
fun Context.canAccessAllFiles(): Boolean =
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    Environment.isExternalStorageManager()
  } else {
    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
      PackageManager.PERMISSION_GRANTED
  }

/** Opens the system page where the user can flip the "All files access" switch. */
fun Context.createStorageAccessIntent(): Intent =
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    Intent(
      Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
      "package:$packageName".toUri(),
    )
  } else {
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
  }
