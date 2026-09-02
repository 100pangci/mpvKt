package live.mehiz.mpvkt.ui.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import live.mehiz.mpvkt.network.NetworkSource
import live.mehiz.mpvkt.network.NetworkStore
import live.mehiz.mpvkt.network.NetworkType

class NetworkViewModel(
  private val networkStore: NetworkStore,
) : ViewModel() {
  val sources: StateFlow<List<NetworkSource>> = networkStore.sources()
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList(),
    )

  fun addSource(draft: NewNetworkServer) {
    viewModelScope.launch(Dispatchers.IO) {
      networkStore.add(
        NetworkSource(
          id = System.currentTimeMillis(),
          type = draft.type,
          name = draft.name,
          host = draft.host,
          port = draft.port,
          basePath = draft.basePath,
          secure = draft.secure,
          username = draft.username,
          encodedPassword = NetworkSource.encodePassword(draft.password),
        ),
      )
    }
  }

  fun removeSource(id: Long) {
    viewModelScope.launch(Dispatchers.IO) {
      networkStore.remove(id)
    }
  }
}

/** Dialog input before an id and the obfuscated password are assigned. */
data class NewNetworkServer(
  val type: NetworkType,
  val name: String,
  val host: String,
  val port: Int,
  val basePath: String,
  val secure: Boolean,
  val username: String,
  val password: String,
)
