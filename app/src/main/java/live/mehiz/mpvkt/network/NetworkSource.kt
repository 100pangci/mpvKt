package live.mehiz.mpvkt.network

import android.util.Base64
import kotlinx.serialization.Serializable

enum class NetworkType {
  WEBDAV,
  FTP,
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
  }
}
