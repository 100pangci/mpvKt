package live.mehiz.mpvkt.ui.preferences

import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.preferences.AppearancePreferences
import live.mehiz.mpvkt.preferences.preference.collectAsState
import live.mehiz.mpvkt.presentation.Screen
import live.mehiz.mpvkt.presentation.preferences.MultiChoiceSegmentedButton
import live.mehiz.mpvkt.ui.theme.DarkMode
import live.mehiz.mpvkt.ui.utils.LocalBackStack
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.PreferenceCategory
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import org.koin.compose.koinInject
import java.util.Locale

@Serializable
object AppearancePreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val preferences = koinInject<AppearancePreferences>()
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(text = stringResource(R.string.pref_appearance_title)) },
          navigationIcon = {
            IconButton(onClick = backstack::removeLastOrNull) {
              Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
            }
          },
        )
      },
    ) { padding ->
      ProvidePreferenceLocals {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding),
        ) {
          PreferenceCategory(
            title = { Text(text = stringResource(id = R.string.pref_appearance_category_theme)) },
          )
          val darkMode by preferences.darkMode.collectAsState()
          MultiChoiceSegmentedButton(
            choices = DarkMode.entries.map { context.getString(it.titleRes) }.toImmutableList(),
            selectedIndices = persistentListOf(DarkMode.entries.indexOf(darkMode)),
            onClick = { preferences.darkMode.set(DarkMode.entries[it]) },
          )
          val materialYou by preferences.materialYou.collectAsState()
          val isMaterialYouAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
          SwitchPreference(
            value = materialYou,
            onValueChange = { preferences.materialYou.set(it) },
            title = { Text(text = stringResource(id = R.string.pref_appearance_material_you_title)) },
            summary = {
              Text(
                text = stringResource(
                  if (isMaterialYouAvailable) {
                    R.string.pref_appearance_material_you_summary
                  } else {
                    R.string.pref_appearance_material_you_summary_disabled
                  },
                ),
              )
            },
            enabled = isMaterialYouAvailable,
          )
          val currentLanguageTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
          var languageDialogOpen by remember { mutableStateOf(false) }
          Preference(
            title = { Text(text = stringResource(id = R.string.pref_appearance_language)) },
            summary = { Text(text = languageDisplayName(currentLanguageTag, context)) },
            icon = { Icon(Icons.Default.Translate, null) },
            onClick = { languageDialogOpen = true },
          )
          if (languageDialogOpen) {
            AlertDialog(
              onDismissRequest = { languageDialogOpen = false },
              title = { Text(text = stringResource(id = R.string.pref_appearance_language)) },
              text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                  languageTags.forEach { tag ->
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable {
                          languageDialogOpen = false
                          val locales = if (tag.isEmpty()) {
                            LocaleListCompat.getEmptyLocaleList()
                          } else {
                            LocaleListCompat.forLanguageTags(tag)
                          }
                          AppCompatDelegate.setApplicationLocales(locales)
                        },
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      RadioButton(selected = tag == currentLanguageTag, onClick = null)
                      Text(
                        text = languageDisplayName(tag, context),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 12.dp),
                      )
                    }
                  }
                }
              },
              confirmButton = {},
              dismissButton = {
                TextButton(onClick = { languageDialogOpen = false }) {
                  Text(text = stringResource(id = R.string.generic_cancel))
                }
              },
            )
          }
        }
      }
    }
  }

  private fun languageDisplayName(tag: String, context: Context): String {
    if (tag.isEmpty()) return context.getString(R.string.pref_appearance_language_system)
    val locale = Locale.forLanguageTag(tag)
    return locale.getDisplayName(locale)
  }
}

private val languageTags = listOf(
  "", "ar", "de", "en", "es", "fr", "hi", "id", "it",
  "ja-JP", "ko", "pl", "pt-BR", "ru", "th", "tr", "uk", "vi", "zh-CN",
)
