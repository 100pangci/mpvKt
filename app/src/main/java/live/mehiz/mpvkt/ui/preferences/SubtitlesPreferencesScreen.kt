package live.mehiz.mpvkt.ui.preferences

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.database.dao.FontDao
import live.mehiz.mpvkt.player.FontConfigManager
import live.mehiz.mpvkt.player.FontIndexer
import live.mehiz.mpvkt.preferences.SubtitlesPreferences
import live.mehiz.mpvkt.preferences.preference.collectAsState
import live.mehiz.mpvkt.presentation.Screen
import live.mehiz.mpvkt.ui.utils.LocalBackStack
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import me.zhanghai.compose.preference.TwoTargetIconButtonPreference
import org.koin.compose.koinInject

@Serializable
object SubtitlesPreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val fontIndexer = koinInject<FontIndexer>()
    val fontConfigManager = koinInject<FontConfigManager>()
    val backstack = LocalBackStack.current
    val preferences = koinInject<SubtitlesPreferences>()

    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Text(stringResource(R.string.pref_subtitles))
          },
          navigationIcon = {
            IconButton(onClick = backstack::removeLastOrNull) {
              Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
            }
          },
        )
      },
    ) { padding ->
      ProvidePreferenceLocals {
        val scope = rememberCoroutineScope()
        val fontDao = koinInject<FontDao>()
        val isScanning by fontIndexer.isScanning.collectAsState()
        val scanDone by fontIndexer.scanDone.collectAsState()
        val scanTotal by fontIndexer.scanTotal.collectAsState()
        var indexedCount by remember { mutableIntStateOf(0) }
        LaunchedEffect(isScanning) {
          if (!isScanning) {
            indexedCount = runCatching { fontDao.countDistinctPaths() }.getOrDefault(0)
          }
        }
        val locationPicker = rememberLauncherForActivityResult(
          ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
          if (uri == null) return@rememberLauncherForActivityResult

          val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
          context.contentResolver.takePersistableUriPermission(uri, flags)
          preferences.fontsFolder.set(uri.toString())
          scope.launch(Dispatchers.IO) {
            runCatching {
              fontIndexer.reindexUserFolder(uri.toString())
              fontConfigManager.regenerate()
              preferences.fontIndexScanAt.set(System.currentTimeMillis())
            }
          }
        }
        val fontsFolder by preferences.fontsFolder.collectAsState()
        Column(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding),
        ) {
          val preferredLanguages by preferences.preferredLanguages.collectAsState()
          TextFieldPreference(
            value = preferredLanguages,
            onValueChange = preferences.preferredLanguages::set,
            textToValue = { it },
            title = { Text(stringResource(R.string.pref_preferred_languages)) },
            summary = { if (preferredLanguages.isNotBlank()) Text(preferredLanguages) },
            textField = { value, onValueChange, _ ->
              Column {
                Text(stringResource(R.string.pref_subtitles_preferred_language))
                TextField(
                  value,
                  onValueChange,
                  modifier = Modifier.fillMaxWidth()
                )
              }
            },
          )
          TwoTargetIconButtonPreference(
            title = { Text(stringResource(R.string.pref_subtitles_fonts_dir)) },
            onClick = { locationPicker.launch(null) },
            summary = {
              if (fontsFolder.isBlank()) return@TwoTargetIconButtonPreference
              Text(getSimplifiedPathFromUri(fontsFolder))
            },
            iconButtonIcon = { Icon(Icons.Default.Clear, null) },
            onIconButtonClick = {
              preferences.fontsFolder.delete()
              // Dropping the folder must also drop what it contributed, or
              // the picker and the index keep listing fonts that can no
              // longer be resolved.
              scope.launch(Dispatchers.IO) {
                runCatching {
                  fontIndexer.clearSource(FontIndexer.SOURCE_USER)
                  fontConfigManager.regenerate()
                }
              }
            },
            iconButtonEnabled = fontsFolder.isNotBlank()
          )
          val useSystemFonts by preferences.useSystemFonts.collectAsState()
          Preference(
            title = { Text(stringResource(R.string.font_index_rebuild)) },
            summary = {
              val noSource = fontsFolder.isBlank() && !useSystemFonts
              Text(
                when {
                  isScanning -> stringResource(R.string.font_index_scanning) + " ($scanDone/$scanTotal)"
                  noSource -> stringResource(R.string.font_index_no_source)
                  else -> stringResource(R.string.font_index_count, indexedCount)
                },
              )
            },
            enabled = !isScanning,
            onClick = {
              scope.launch(Dispatchers.IO) {
                runCatching {
                  fontsFolder.takeIf { it.isNotBlank() }?.let { fontIndexer.reindexUserFolder(it) }
                  if (preferences.useSystemFonts.get()) fontIndexer.reindexSystemFonts()
                  fontConfigManager.regenerate()
                  // Write the timestamp back, or the next playback's
                  // preflight refresh would rescan all over again.
                  preferences.fontIndexScanAt.set(System.currentTimeMillis())
                }
              }
            },
          )
          SwitchPreference(
            value = useSystemFonts,
            onValueChange = { enabled ->
              preferences.useSystemFonts.set(enabled)
              scope.launch(Dispatchers.IO) {
                runCatching {
                  if (enabled) {
                    fontIndexer.reindexSystemFonts()
                  } else {
                    // Turning the switch off must take system fonts out of
                    // the picker and the index, mirroring what the switch
                    // claims to do.
                    fontIndexer.clearSource(FontIndexer.SOURCE_SYSTEM)
                  }
                  fontConfigManager.regenerate()
                }
              }
            },
            title = { Text(stringResource(R.string.pref_subtitles_use_system_fonts_title)) },
            summary = { Text(stringResource(R.string.pref_subtitles_use_system_fonts_summary)) },
          )
          val autoloadExternal by preferences.autoLoadExternal.collectAsState()
          SwitchPreference(
            value = autoloadExternal,
            onValueChange = { preferences.autoLoadExternal.set(it) },
            title = { Text(text = stringResource(id = R.string.pref_subtitles_autoload_title)) },
            summary = {
              Text(
                text = stringResource(id = R.string.pref_subtitles_autoload_summary),
              )
            },
          )
        }
      }
    }
  }
}
