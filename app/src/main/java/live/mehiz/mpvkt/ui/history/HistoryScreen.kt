package live.mehiz.mpvkt.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.serialization.Serializable
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.presentation.Screen
import live.mehiz.mpvkt.presentation.components.ConfirmDialog
import live.mehiz.mpvkt.presentation.history.HistoryScreen
import live.mehiz.mpvkt.ui.home.HomeScreen
import live.mehiz.mpvkt.ui.utils.LocalBackStack
import org.koin.compose.viewmodel.koinViewModel

@Serializable
object HistoryScreen : Screen {
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    val viewModel = koinViewModel<HistoryViewModel>()
    val history by viewModel.history.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    HistoryScreen(
      history = history,
      onResume = { HomeScreen.playFile(it.uri, context) },
      onDelete = viewModel::delete,
      onClickClearAll = { showClearDialog = true },
      onNavigateBack = backstack::removeLastOrNull,
    )

    if (showClearDialog) {
      // Reuse the advanced-preferences wording: clearing here deletes the
      // exact same data, including saved positions and track choices.
      ConfirmDialog(
        title = stringResource(R.string.pref_advanced_clear_playback_history),
        subtitle = stringResource(R.string.pref_advanced_clear_playback_history_confirm_subtitle),
        onConfirm = {
          viewModel.clearAll()
          showClearDialog = false
        },
        onCancel = { showClearDialog = false },
      )
    }
  }
}
