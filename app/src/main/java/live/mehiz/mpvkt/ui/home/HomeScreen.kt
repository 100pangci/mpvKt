package live.mehiz.mpvkt.ui.home

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.github.k1rakishou.fsaf.FileManager
import `is`.xyz.mpv.Utils.PROTOCOLS
import kotlinx.serialization.Serializable
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.player.FontIndexer
import live.mehiz.mpvkt.presentation.Screen
import live.mehiz.mpvkt.ui.history.HistoryScreen
import live.mehiz.mpvkt.ui.network.NetworkScreen
import live.mehiz.mpvkt.ui.player.PlayerActivity
import live.mehiz.mpvkt.ui.preferences.PreferencesScreen
import live.mehiz.mpvkt.ui.theme.spacing
import live.mehiz.mpvkt.ui.utils.LocalBackStack
import live.mehiz.mpvkt.ui.utils.canAccessAllFiles
import live.mehiz.mpvkt.ui.utils.createStorageAccessIntent
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
      bottomBar = { FontIndexScanProgress() },
    ) { padding ->
      Box(
        Modifier
          .fillMaxSize()
          .padding(padding),
      ) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) },
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
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
          val fileManager = koinInject<FileManager>()
          val directoryPicker = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocumentTree(),
          ) {
            if (it == null) return@rememberLauncherForActivityResult
            // Keep the tree readable when Android recreates the task/process.
            // Some providers only offer a temporary grant; browsing should still
            // work for this session, and restoration handles an expired grant.
            try {
              context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
              // The provider did not offer a persistable read grant.
            }
            val directory = fileManager.resolvePickerDirectory(it)
            if (directory == null) {
              Toast.makeText(context, R.string.home_directory_unavailable, Toast.LENGTH_LONG).show()
            } else {
              backstack.add(FilePickerScreen(directory.getFullPath()))
            }
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
        StorageAccessBanner(
          Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth(),
        )
      }
    }
  }

  /**
   * Font index scan progress, pinned to the bottom edge: the centered home
   * buttons should neither host it nor move while a scan runs.
   */
  @Composable
  private fun FontIndexScanProgress() {
    val fontIndexer = koinInject<FontIndexer>()
    val isScanning by fontIndexer.isScanning.collectAsState()
    val scanDone by fontIndexer.scanDone.collectAsState()
    val scanTotal by fontIndexer.scanTotal.collectAsState()
    if (!isScanning || scanTotal <= 0) return
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(
          start = MaterialTheme.spacing.large,
          end = MaterialTheme.spacing.large,
          bottom = MaterialTheme.spacing.medium,
        ),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = stringResource(R.string.font_index_scanning) + " ($scanDone/$scanTotal)",
        style = MaterialTheme.typography.bodySmall,
      )
      LinearProgressIndicator(
        progress = { scanDone.toFloat() / scanTotal },
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = MaterialTheme.spacing.smaller),
      )
    }
  }

  /**
   * Storage onboarding without nagging: a persistent banner while "All files
   * access" is missing, and the explanation dialog only when the user asks
   * for the grant. The permission is never requested on launch — nothing at
   * startup needs it. It becomes relevant when files are opened from other
   * apps (a single-file SAF grant covers only the file, not its directory,
   * so sibling subtitles and fonts cannot be found); that flow prompts in
   * the player instead.
   */
  @Composable
  private fun StorageAccessBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var storageGranted by remember { mutableStateOf(context.canAccessAllFiles()) }
    var showPrompt by remember { mutableStateOf(false) }
    // Special-access settings do not reliably deliver an activity result on
    // every device. Refresh on resume; the banner disappears once granted.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
      storageGranted = context.canAccessAllFiles()
    }
    fun requestAccess() {
      runCatching { context.startActivity(context.createStorageAccessIntent()) }
        .onFailure {
          runCatching {
            context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
          }
        }
      showPrompt = false
    }
    if (storageGranted) return
    // Pinned to the bottom edge of the home screen; the dialog overlays on top.
    Card(
      modifier = modifier
        .padding(
          horizontal = MaterialTheme.spacing.large,
          vertical = MaterialTheme.spacing.medium,
        ),
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
        TextButton(onClick = { showPrompt = true }) {
          Text(stringResource(R.string.storage_all_files_learn_more))
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
          TextButton(onClick = { showPrompt = false }) {
            Text(stringResource(R.string.generic_cancel))
          }
        },
      )
    }
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
