package live.mehiz.mpvkt.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import live.mehiz.mpvkt.database.entities.PlaybackStateEntity
import live.mehiz.mpvkt.domain.playbackstate.repository.PlaybackStateRepository

class HistoryViewModel(
  private val playbackStateRepository: PlaybackStateRepository,
) : ViewModel() {
  val history: StateFlow<List<PlaybackStateEntity>> = playbackStateRepository.getAllPlaybackStates()
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList(),
    )

  fun delete(item: PlaybackStateEntity) {
    viewModelScope.launch(Dispatchers.IO) {
      playbackStateRepository.deletePlaybackState(item.mediaTitle)
    }
  }

  fun clearAll() {
    viewModelScope.launch(Dispatchers.IO) {
      playbackStateRepository.clearAllPlaybackStates()
    }
  }
}
