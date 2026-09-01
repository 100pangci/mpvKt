package live.mehiz.mpvkt.di

import kotlinx.serialization.json.Json
import live.mehiz.mpvkt.player.FontConfigManager
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

// generic dependencies for the app's needs
val AppModule = module {
  single {
    Json {
      isLenient = true
      ignoreUnknownKeys = true
    }
  }
  singleOf(::FontConfigManager)
}
