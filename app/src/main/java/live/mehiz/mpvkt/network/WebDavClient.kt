package live.mehiz.mpvkt.network

import android.util.Xml
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class WebDavClient(
  private val source: NetworkSource,
  client: OkHttpClient,
) : RemoteClient {

  private val client = client.newBuilder()
    .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .build()

  private val basicAuth: String? =
    source.username.takeIf { it.isNotEmpty() }?.let { Credentials.basic(it, source.password, Charsets.UTF_8) }

  private fun authorityUrl(path: String): String {
    val scheme = if (source.secure) "https" else "http"
    val base = source.basePath.trimEnd('/')
    return "$scheme://${source.host}:${source.port}$base/${path.trimStart('/')}"
  }

  override fun fileUrl(path: String): String {
    val url = authorityUrl(path)
    if (source.username.isEmpty()) return url
    // mpv resolves user:pass@ in the URL; percent-encode both parts.
    val user = urlEncode(source.username)
    val password = urlEncode(source.password)
    return url.replace("://", "://$user:$password@")
  }

  override fun list(path: String): List<RemoteEntry> {
    val request = Request.Builder()
      .url(authorityUrl(path))
      .apply { basicAuth?.let { header("Authorization", it) } }
      .header("Depth", "1")
      .method("PROPFIND", null)
      .build()
    client.newCall(request).execute().use { response ->
      if (!response.isSuccessful) throw IOException("PROPFIND failed: HTTP ${response.code}")
      val body = response.body?.string().orEmpty()
      return parseMultiStatus(body, selfPathOf(path))
    }
  }

  override fun download(path: String, destination: File, expectedSize: Long): Boolean {
    val request = Request.Builder()
      .url(authorityUrl(path))
      .apply { basicAuth?.let { header("Authorization", it) } }
      .build()
    val downloaded = runCatching {
      client.newCall(request).execute().use { response ->
        val body = response.body
        if (!response.isSuccessful || body == null) {
          false
        } else {
          body.byteStream().use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
          }
          true
        }
      }
    }.getOrDefault(false)
    // A truncated copy must never count as staged.
    return downloaded && destination.length() > 0 &&
      (expectedSize <= 0 || destination.length() == expectedSize)
  }

  private fun parseMultiStatus(xml: String, selfPath: String): List<RemoteEntry> {
    val parser = Xml.newPullParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
    parser.setInput(xml.reader())
    val entries = mutableListOf<RemoteEntry>()
    var href: String? = null
    var isDirectory = false
    var size = 0L

    fun startTag() {
      // Namespaces are off, so the name is a raw qname ("d:href", "D:response",
      // "ns0:getcontentlength" depending on the server): strip the prefix.
      when (parser.name.substringAfterLast(':').lowercase()) {
        "href" -> href = parser.nextText()
        "collection" -> isDirectory = true
        "getcontentlength" -> size = parser.nextText()?.toLongOrNull() ?: 0L
      }
    }

    fun endTag() {
      if (!parser.name.substringAfterLast(':').equals("response", true)) return
      href?.let { h ->
        if (!isSelf(h, selfPath)) {
          displayName(h)?.let { entries.add(RemoteEntry(it, isDirectory, size)) }
        }
      }
      href = null
      isDirectory = false
      size = 0L
    }

    while (parser.eventType != XmlPullParser.END_DOCUMENT) {
      when (parser.eventType) {
        XmlPullParser.START_TAG -> startTag()
        XmlPullParser.END_TAG -> endTag()
      }
      parser.next()
    }
    return entries
  }

  private fun isSelf(href: String, selfPath: String): Boolean {
    // Servers answer with either a bare path or a full URL: normalize to the
    // decoded path and drop the trailing slash before comparing.
    val normalized = runCatching { URI(href).path ?: href }
      .getOrElse { URLDecoder.decode(href, "UTF-8") }
    return normalized.trimEnd('/') == selfPath.trimEnd('/')
  }

  /** The decoded path part of the request URL, without authority. */
  private fun selfPathOf(path: String): String =
    runCatching { URI(authorityUrl(path)).path }.getOrDefault("/${path.trimStart('/')}")

  /** Extracts the percent-decoded file name from a WebDAV href. */
  private fun displayName(href: String): String? {
    val raw = runCatching { URI(href).path ?: href }
      .getOrElse { URLDecoder.decode(href, "UTF-8") }
      .trimEnd('/')
      .substringAfterLast('/')
    return raw.takeIf { it.isNotEmpty() }
  }

  private fun urlEncode(value: String): String =
    URLEncoder.encode(value, "UTF-8").replace("+", "%20")

  private companion object {
    const val CONNECT_TIMEOUT_SECONDS = 10L
    const val READ_TIMEOUT_SECONDS = 30L
  }
}
