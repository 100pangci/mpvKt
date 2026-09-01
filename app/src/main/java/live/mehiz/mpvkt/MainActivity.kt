package live.mehiz.mpvkt

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import live.mehiz.mpvkt.player.FontIndexer
import live.mehiz.mpvkt.preferences.AppearancePreferences
import live.mehiz.mpvkt.preferences.SubtitlesPreferences
import live.mehiz.mpvkt.preferences.preference.collectAsState
import live.mehiz.mpvkt.presentation.Screen
import live.mehiz.mpvkt.ui.home.HomeScreen
import live.mehiz.mpvkt.ui.player.FONT_INDEX_SCAN_INTERVAL
import live.mehiz.mpvkt.ui.preferences.SubtitlesPreferencesScreen
import live.mehiz.mpvkt.ui.theme.DarkMode
import live.mehiz.mpvkt.ui.theme.MpvKtTheme
import live.mehiz.mpvkt.ui.utils.LocalBackStack
import org.koin.android.ext.android.inject

class MainActivity : AppCompatActivity() {

  companion object {
    const val EXTRA_OPEN_SUBTITLE_SETTINGS = "live.mehiz.mpvkt.extra.OPEN_SUBTITLE_SETTINGS"
  }
  private val appearancePreferences by inject<AppearancePreferences>()
  private val fontIndexer by inject<FontIndexer>()
  private val subtitlesPreferences by inject<SubtitlesPreferences>()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    lifecycleScope.launch(Dispatchers.IO) {
      runCatching {
        val now = System.currentTimeMillis()
        if (now - subtitlesPreferences.fontIndexScanAt.get() < FONT_INDEX_SCAN_INTERVAL) return@launch
        subtitlesPreferences.fontsFolder.get().takeIf { it.isNotBlank() }?.let {
          fontIndexer.reindexUserFolder(it)
        }
        if (subtitlesPreferences.useSystemFonts.get()) fontIndexer.reindexSystemFonts()
        subtitlesPreferences.fontIndexScanAt.set(now)
      }
    }
    val openSubtitleSettings = intent?.getBooleanExtra(EXTRA_OPEN_SUBTITLE_SETTINGS, false) == true
    setContent {
      val dark by appearancePreferences.darkMode.collectAsState()
      val isSystemInDarkTheme = isSystemInDarkTheme()
      enableEdgeToEdge(
        SystemBarStyle.auto(
          lightScrim = Color.White.toArgb(),
          darkScrim = Color.White.toArgb(),
        ) { dark == DarkMode.Dark || (dark == DarkMode.System && isSystemInDarkTheme) },
      )
      MpvKtTheme { Surface { Navigator(openSubtitleSettings) } }
    }
  }

  @Composable
  fun Navigator(openSubtitleSettings: Boolean) {
    val backstack = rememberNavBackStack<Screen>(HomeScreen)
    LaunchedEffect(openSubtitleSettings) {
      if (openSubtitleSettings) backstack.add(SubtitlesPreferencesScreen)
    }
    CompositionLocalProvider(LocalBackStack provides backstack) {
      NavDisplay(
        backStack = backstack,
        onBack = { backstack.removeLastOrNull() },
        entryProvider = { route -> NavEntry(route) { (it as Screen).Content() } },
        popTransitionSpec = {
          fadeIn(animationSpec = tween(220)) +
            slideIn(animationSpec = tween(220)) { IntOffset(-it.width / 2, 0) } togetherWith
            fadeOut(animationSpec = tween(220)) +
            slideOut(animationSpec = tween(220)) { IntOffset(it.width / 2, 0) }
        },
        transitionSpec = {
          fadeIn(animationSpec = tween(220)) +
            slideIn(animationSpec = tween(220)) { IntOffset(it.width / 2, 0) } togetherWith
            fadeOut(animationSpec = tween(220)) +
            slideOut(animationSpec = tween(220)) { IntOffset(-it.width / 2, 0) }
        },
        predictivePopTransitionSpec = {
          (
            fadeIn(animationSpec = tween(220)) +
              scaleIn(
                animationSpec = tween(220, delayMillis = 30),
                initialScale = .9f,
                TransformOrigin(-1f, .5f),
              )
            ).togetherWith(
            fadeOut(animationSpec = tween(220)) +
              scaleOut(animationSpec = tween(220, delayMillis = 30), targetScale = .9f, TransformOrigin(-1f, .5f)),
          )
        },
      )
    }
  }
}
