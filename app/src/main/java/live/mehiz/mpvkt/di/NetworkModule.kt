package live.mehiz.mpvkt.di

import live.mehiz.mpvkt.network.NetworkStore
import live.mehiz.mpvkt.network.RemoteClientFactory
import live.mehiz.mpvkt.network.RemoteFontStager
import live.mehiz.mpvkt.preferences.NetworkPreferences
import okhttp3.OkHttpClient
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

val NetworkModule = module {
  singleOf(::NetworkPreferences)

  single {
    OkHttpClient.Builder()
      .connectTimeout(10, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .build()
  }

  singleOf(::NetworkStore)
  singleOf(::RemoteClientFactory)
  singleOf(::RemoteFontStager)
}
