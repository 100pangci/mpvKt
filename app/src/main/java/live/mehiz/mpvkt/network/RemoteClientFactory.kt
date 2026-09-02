package live.mehiz.mpvkt.network

import okhttp3.OkHttpClient

class RemoteClientFactory(okHttpClient: OkHttpClient) {
  private val okHttpClient = okHttpClient.newBuilder().build()

  fun create(source: NetworkSource): RemoteClient = when (source.type) {
    NetworkType.WEBDAV -> WebDavClient(source, okHttpClient)
    NetworkType.FTP -> FtpClient(source)
  }
}
