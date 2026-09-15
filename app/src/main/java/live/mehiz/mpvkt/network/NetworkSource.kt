package live.mehiz.mpvkt.network

import android.util.Base64
import kotlinx.serialization.Serializable

enum class NetworkType {
  WEBDAV,
  FTP,
  SMB,
}

/**
 * SMB dialect preference. [AUTO] keeps jcifs-ng's default range
 * (SMB1 up to SMB2.1); the explicit values pin both ends of the
 * negotiated range, SMB3 spanning 3.0 to 3.1.1.
 */
enum class SmbDialect {
  AUTO,
  SMB1,
  SMB2,
  SMB3,
}

@Serializable
data class NetworkSource(
  val id: Long,
  val type: NetworkType,
  val name: String,
  val host: String,
  val port: Int,
  val basePath: String,
  val secure: Boolean = false,
  val smbDialect: SmbDialect = SmbDialect.AUTO,
  val username: String = "",
  // Stored as a serialized instance: keep this field's name stable.
  val encodedPassword: String = "",
) {
  /**
   * Obfuscated with Base64 only: it keeps the password out of a casual
   * glance at the preferences file, it is NOT encryption and can be trivially
   * reversed.
   */
  val password: String
    get() = runCatching {
      String(Base64.decode(encodedPassword, Base64.NO_WRAP))
    }.getOrDefault("")

  /** True when the root path has a meaningful value beyond "/". */
  val hasBasePath: Boolean get() = basePath.isNotBlank() && basePath != "/"

  companion object {
    fun encodePassword(plain: String): String =
      Base64.encodeToString(plain.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    /** Joins two remote path segments keeping exactly one separator. */
    fun joinPath(parent: String, child: String): String =
      parent.trimEnd('/') + "/" + child.trimStart('/')

    /**
     * Splits a host input that may have been pasted as a full URL
     * ("http://192.168.1.1:5244/dav") into its host, port and base path
     * parts; plain host names pass through untouched. IPv6 literals are
     * not handled.
     */
    fun splitHost(input: String): HostParts {
      val trimmed = input.trim()
      if (trimmed.isEmpty()) return HostParts("", null, null)
      val withoutScheme = trimmed.substringAfter("://", trimmed)
      val basePath = withoutScheme
        .substringAfter('/', "")
        .takeIf { it.isNotEmpty() }
        ?.trimEnd('/')
        ?.let { "/$it" }
      val hostPort = withoutScheme.substringBefore('/')
      val host = hostPort.substringBeforeLast(':')
      val port = hostPort.substringAfterLast(':', "").toIntOrNull()
        ?.takeIf { it in 1..65535 }
      return HostParts(host, port, basePath)
    }
  }
}

data class HostParts(
  val host: String,
  val port: Int?,
  val basePath: String?,
)
