package live.mehiz.mpvkt.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.github.k1rakishou.fsaf.FileManager
import `is`.xyz.mpv.Utils.PROTOCOLS
import kotlinx.serialization.Serializable
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.player.FontIndexer
import live.mehiz.mpvkt.preferences.AppPreferences
import live.mehiz.mpvkt.presentation.Screen
import live.mehiz.mpvkt.ui.history.HistoryScreen
import live.mehiz.mpvkt.ui.network.NetworkScreen
import live.mehiz.mpvkt.ui.player.PlayerActivity
import live.mehiz.mpvkt.ui.preferences.PreferencesScreen
import live.mehiz.mpvkt.ui.theme.spacing
import live.mehiz.mpvkt.ui.utils.LocalBackStack
import org.koin.compose.koinInject

@Serializable
object HomeScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    val focusManager = LocalFocusManager.current
    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(text = stringResource(id = R.string.app_name)) },
          actions = {
            IconButton(onClick = { backstack.add(PreferencesScreen) }) {
              Icon(Icons.Default.Settings, null)
            }
          },
          navigationIcon = {
            Image(
              painter = painterResource(id = R.drawable.ic_launcher_foreground),
              contentDescription = "app_logo",
            )
          },
        )
      },
    ) { padding ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
          .padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        StorageAccessOnboarding()
        val uri = rememberTextFieldState()
        var isUrlValid by remember { mutableStateOf(true) }
        LaunchedEffect(uri.text) {
          isUrlValid = uri.text.isEmpty() || isURLValid(uri.text.toString())
        }
        OutlinedTextField(
          state = uri,
          label = { Text(stringResource(R.string.home_url_input_label)) },
          supportingText = {
            Text(if (isUrlValid) "" else stringResource(R.string.home_invalid_protocol))
          },
          trailingIcon = {
            if (!isUrlValid) Icon(Icons.Filled.Info, null)
          },
          isError = !isUrlValid
        )
        Button(
          onClick = { playFile(uri.text.toString(), context) },
          enabled = uri.text.isNotBlank() && isUrlValid,
        ) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Default.Link, null)
            Text(text = stringResource(R.string.home_open_url))
          }
        }
        val documentPicker = rememberLauncherForActivityResult(
          ActivityResultContracts.OpenDocument(),
        ) {
          if (it == null) return@rememberLauncherForActivityResult
          playFile(it.toString(), context)
        }
        val fontIndexer = koinInject<FontIndexer>()
        val isScanning by fontIndexer.isScanning.collectAsState()
        val scanDone by fontIndexer.scanDone.collectAsState()
        val scanTotal by fontIndexer.scanTotal.collectAsState()
        if (isScanning && scanTotal > 0) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = MaterialTheme.spacing.medium),
          ) {
            Text(
              text = stringResource(R.string.font_index_scanning) + " ($scanDone/$scanTotal)",
              style = MaterialTheme.typography.bodySmall,
            )
            LinearProgressIndicator(
              progress = {
                if (scanTotal > 0) scanDone.toFloat() / scanTotal else 0f
              },
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.large)
                .padding(top = MaterialTheme.spacing.smaller),
            )
          }
        }
        OutlinedButton(
          onClick = { documentPicker.launch(arrayOf("*/*")) },
        ) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Default.FileOpen, null)
            Text(text = stringResource(R.string.home_pick_file))
          }
        }
        val fileManager = FileManager(context)
        val directoryPicker = rememberLauncherForActivityResult(
          ActivityResultContracts.OpenDocumentTree(),
        ) {
          if (it == null) return@rememberLauncherForActivityResult
          backstack.add(FilePickerScreen(fileManager.fromUri(it)!!.getFullPath()))
        }
        OutlinedButton(onClick = { directoryPicker.launch(null) }) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Default.FolderOpen, null)
            Text(text = stringResource(R.string.home_open_file_picker))
          }
        }
        OutlinedButton(onClick = { backstack.add(HistoryScreen) }) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Default.History, null)
            Text(text = stringResource(R.string.history_title))
          }
        }
        OutlinedButton(onClick = { backstack.add(NetworkScreen) }) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Default.Lan, null)
            Text(text = stringResource(R.string.home_network_sources))
          }
        }
      }
    }
  }

  /**
   * Single owner of the storage onboarding: a persistent banner plus a
   * per-launch dialog until the permission is granted or the user opts out.
   * The previous one-shot flag burned itself the instant the dialog
   * appeared, so a single dismissal or a later revocation silenced the
   * request forever.
   *
   * Android 11+ routes to the "All files access" settings page; older
   * versions use the runtime storage permission dialog, which previously
   * was only ever requested when playing a file — never on first launch.
   */
  @Composable
  private fun StorageAccessOnboarding() {
    val context = LocalContext.current
    val appPreferences = koinInject<AppPreferences>()
    var storageGranted by remember { mutableStateOf(hasAllFilesAccess(context)) }
    var dontAsk by remember { mutableStateOf(appPreferences.storageAccessDontAsk.get()) }
    var showPrompt by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
      storageGranted = hasAllFilesAccess(context)
      showPrompt = !storageGranted && !dontAsk
    }
    val settingsLauncher = rememberLauncherForActivityResult(
      ActivityResultContracts.StartActivityForResult(),
    ) {
      storageGranted = hasAllFilesAccess(context)
      if (storageGranted) showPrompt = false
    }
    val permissionLauncher = rememberLauncherForActivityResult(
      ActivityResultContracts.RequestPermission(),
    ) { granted ->
      storageGranted = granted
      if (granted) showPrompt = false
    }
    fun requestAccess() {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val intent = Intent(
          Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION,
          "package:${context.packageName}".toUri(),
        )
        runCatching { settingsLauncher.launch(intent) }
          .onFailure {
            settingsLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
          }
      } else {
        permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
      }
    }
    if (storageGranted || dontAsk) return
    // The banner lives in the home column; the dialog overlays on top.
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = MaterialTheme.spacing.large),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
      ),
    ) {
      Row(
        modifier = Modifier.padding(MaterialTheme.spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(R.string.storage_all_files_banner),
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.weight(1f),
        )
        TextButton(onClick = ::requestAccess) {
          Text(stringResource(R.string.storage_all_files_grant))
        }
      }
    }
    if (showPrompt) {
      AlertDialog(
        onDismissRequest = { showPrompt = false },
        title = { Text(stringResource(R.string.storage_all_files_title)) },
        text = { Text(stringResource(R.string.storage_all_files_message)) },
        confirmButton = {
          TextButton(onClick = ::requestAccess) {
            Text(stringResource(R.string.storage_all_files_grant))
          }
        },
        dismissButton = {
          Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            TextButton(
              onClick = {
                dontAsk = true
                appPreferences.storageAccessDontAsk.set(true)
                showPrompt = false
              },
            ) {
              Text(stringResource(R.string.storage_all_files_dont_ask))
            }
            TextButton(onClick = { showPrompt = false }) {
              Text(stringResource(R.string.generic_cancel))
            }
          }
        },
      )
    }
  }

  private fun hasAllFilesAccess(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      Environment.isExternalStorageManager()
    } else {
      ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
        PackageManager.PERMISSION_GRANTED
    }

  // Basically a copy of:
  // https://github.com/mpv-android/mpv-android/blob/32cbff3cedea73b4616b34542cb95bf1d00504cc/app/src/main/java/is/xyz/mpv/Utils.kt#L406
  private fun isURLValid(url: String): Boolean {
    val uri = url.toUri()
    return uri.isHierarchical && !uri.isRelative &&
      !(uri.host.isNullOrBlank() && uri.path.isNullOrBlank()) &&
      PROTOCOLS.contains(uri.scheme)
  }

  fun playFile(
    filepath: String,
    context: Context,
  ) {
    val i = Intent(Intent.ACTION_VIEW, filepath.toUri())
    i.setClass(context, PlayerActivity::class.java)
    context.startActivity(i)
  }
}
