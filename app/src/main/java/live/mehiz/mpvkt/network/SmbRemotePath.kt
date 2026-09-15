package live.mehiz.mpvkt.network

/**
 * An SMB location split into the share and the path inside it. The share is
 * always the first segment of the source's root path plus the browsed path,
 * so a blank root path browses share by share ("media/Movies" plus a picked
 * "S1" resolves to share "media" and path "Movies/S1"). Backslashes are
 * accepted and normalized; jcifs-ng itself takes "/" separators.
 */
data class SmbRemotePath(val share: String, val path: String) {
  companion object {
    /**
     * @return null when neither the root path nor the browsed path names a
     * share, i.e. the server root where shares are listed.
     */
    fun resolve(basePath: String, path: String): SmbRemotePath? {
      val segments = "$basePath/$path"
        .replace('\\', '/')
        .split('/')
        .filter { it.isNotBlank() }
      val share = segments.firstOrNull() ?: return null
      return SmbRemotePath(share, segments.drop(1).joinToString("/"))
    }
  }
}
