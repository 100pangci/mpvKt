// Modified from upstream mpvKt; see LICENSE-APACHE.
package live.mehiz.mpvkt.ui.preferences

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.mikepenz.aboutlibraries.ui.compose.android.rememberLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import compose.icons.SimpleIcons
import compose.icons.simpleicons.Github
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import live.mehiz.mpvkt.BuildConfig
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.presentation.Screen
import live.mehiz.mpvkt.presentation.crash.CrashActivity.Companion.collectDeviceInfo
import live.mehiz.mpvkt.ui.theme.spacing
import live.mehiz.mpvkt.ui.utils.LocalBackStack
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.ProvidePreferenceLocals

@Serializable
object AboutScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val backstack = LocalBackStack.current
    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(text = stringResource(id = R.string.pref_about_title)) },
          navigationIcon = {
            IconButton(onClick = backstack::removeLastOrNull) {
              Icon(imageVector = Icons.AutoMirrored.Default.ArrowBack, contentDescription = null)
            }
          },
        )
      },
    ) { paddingValues ->
      Column(
        modifier = Modifier
          .padding(paddingValues)
          .verticalScroll(rememberScrollState()),
      ) {
        Column(
          Modifier
            .fillMaxWidth()
            .padding(bottom = MaterialTheme.spacing.large),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Image(
            painterResource(id = R.drawable.ic_launcher_foreground),
            null,
            modifier = Modifier.size(160.dp),
          )
          Text(
            text = stringResource(id = R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
          )
        }
        HorizontalDivider()
        ProvidePreferenceLocals {
          Preference(
            title = { Text(text = stringResource(id = R.string.pref_about_app_version)) },
            summary = {
              Text(
                text = stringResource(
                  id = R.string.pref_about_app_version_formatted,
                  BuildConfig.BUILD_TYPE.replaceFirstChar { it.uppercaseChar() },
                  BuildConfig.VERSION_NAME,
                ),
              )
            },
            onClick = {
              scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, collectDeviceInfo())))
              }
            },
          )
          Preference(
            title = { Text(text = stringResource(id = R.string.pref_about_license_project)) },
            summary = { Text(text = ProjectLicense.MPL.repository) },
            onClick = { backstack.add(ProjectLicenseScreen(ProjectLicense.MPL)) },
          )
          Preference(
            title = { Text(text = stringResource(id = R.string.pref_about_license_original)) },
            summary = { Text(text = ProjectLicense.APACHE.repository) },
            onClick = { backstack.add(ProjectLicenseScreen(ProjectLicense.APACHE)) },
          )
          Preference(
            title = { Text(text = stringResource(id = R.string.pref_about_oss_libraries)) },
            onClick = { backstack.add(LibrariesScreen) },
          )
          Preference(
            title = { Text(text = stringResource(id = R.string.pref_about_privacy_policy)) },
            onClick = {
              context.startActivity(
                Intent(
                  Intent.ACTION_VIEW,
                  context.getString(R.string.privacy_policy_url).toUri(),
                ),
              )
            },
          )
        }
        Row(
          Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.Center,
        ) {
          IconButton(
            onClick = {
              context.startActivity(
                Intent(Intent.ACTION_VIEW, context.getString(R.string.github_repo_url).toUri()),
              )
            },
          ) {
            Icon(imageVector = SimpleIcons.Github, contentDescription = null)
          }
        }
      }
    }
  }
}

@Serializable
enum class ProjectLicense {
  MPL,
  APACHE;

  val titleResource: Int
    get() = when (this) {
      MPL -> R.string.pref_about_license_project
      APACHE -> R.string.pref_about_license_original
    }

  val textResource: Int
    get() = when (this) {
      MPL -> R.raw.license_mpl_2_0
      APACHE -> R.raw.license_apache_2_0
    }

  val repository: String
    get() = when (this) {
      MPL -> "100pangci/mpvKt@${BuildConfig.GIT_SHA}"
      APACHE -> "abdallahmehiz/mpvKt@01a93062"
    }

  val repositoryUrl: String
    get() = when (this) {
      MPL -> "https://github.com/100pangci/mpvKt/tree/${BuildConfig.GIT_SHA}"
      APACHE -> "https://github.com/abdallahmehiz/mpvKt/tree/01a93062dd73f642cbb0511f6a05f1fd344b36a9"
    }
}

@Serializable
data class ProjectLicenseScreen(val license: ProjectLicense) : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    val licenseText = produceState(initialValue = "", key1 = license) {
      value = withContext(Dispatchers.IO) {
        context.resources.openRawResource(license.textResource).bufferedReader().use { it.readText() }
      }
    }
    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(text = stringResource(license.titleResource)) },
          navigationIcon = {
            IconButton(onClick = backstack::removeLastOrNull) {
              Icon(imageVector = Icons.AutoMirrored.Default.ArrowBack, contentDescription = null)
            }
          },
        )
      },
    ) { paddingValues ->
      Column(
        modifier = Modifier
          .padding(paddingValues)
          .verticalScroll(rememberScrollState()),
      ) {
        ProvidePreferenceLocals {
          Preference(
            title = { Text(text = stringResource(R.string.pref_about_license_source)) },
            summary = { Text(text = license.repository) },
            onClick = {
              context.startActivity(Intent(Intent.ACTION_VIEW, license.repositoryUrl.toUri()))
            },
          )
        }
        HorizontalDivider()
        Text(
          text = licenseText.value,
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          style = MaterialTheme.typography.bodySmall,
        )
      }
    }
  }
}

@Serializable
object LibrariesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(text = stringResource(R.string.pref_about_oss_libraries)) },
          navigationIcon = {
            IconButton(onClick = backstack::removeLastOrNull) {
              Icon(imageVector = Icons.AutoMirrored.Default.ArrowBack, contentDescription = null)
            }
          },
        )
      },
    ) { paddingValues ->
      LibrariesContainer(
        libraries = rememberLibraries(R.raw.aboutlibraries).value,
        modifier = Modifier.padding(paddingValues),
      )
    }
  }
}
