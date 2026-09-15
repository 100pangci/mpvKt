package live.mehiz.mpvkt.network

/**
 * An SMB location split into the share and the path inside it. SMB has no
 * cross-share browsing, so the share must be the first segment of the
 * source's root path: "/media/Movies" plus a browsed "S1" resolves to share
 * "media" and path "Movies/S1". Backslashes are accepted and normalized;
 * smbj itself takes "/" separators.
 */
data class SmbRemotePath(val share: String, val path: String) {
  companion object {
    /** @return null when no share name can be derived (a share is mandatory). */
    fun resolve(basePath: String, path: String): SmbRemotePath? {
      val segments = basePath.replace('\\', '/').split('/').filter { it.isNotBlank() }
      val share = segments.firstOrNull() ?: return null
      val prefix = segments.drop(1)
      val relative = (prefix + path.replace('\\', '/').split('/').filter { it.isNotBlank() })
        .joinToString("/")
      return SmbRemotePath(share, relative)
    }
  }
}
