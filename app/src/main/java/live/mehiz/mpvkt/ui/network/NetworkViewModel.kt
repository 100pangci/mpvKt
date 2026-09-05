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
import live.mehiz.mpvkt.presentation.network.NetworkDraft

class NetworkViewModel(
  private val networkStore: NetworkStore,
) : ViewModel() {
  val sources: StateFlow<List<NetworkSource>> = networkStore.sources()
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList(),
    )

  fun addSource(draft: NetworkDraft) {
    viewModelScope.launch(Dispatchers.IO) {
      networkStore.add(draft.normalized().toNetworkSource(id = System.currentTimeMillis()))
    }
  }

  fun updateSource(original: NetworkSource, draft: NetworkDraft) {
    viewModelScope.launch(Dispatchers.IO) {
      networkStore.update(original.updatedWith(draft.normalized()))
    }
  }

  fun removeSource(id: Long) {
    viewModelScope.launch(Dispatchers.IO) {
      networkStore.remove(id)
    }
  }
}

/**
 * Fills in the parts a pasted full-URL host carried: the explicit port and
 * base path fields always win, the URL parts only fill blanks.
 */
private fun NetworkDraft.normalized(): NetworkDraft {
  val parts = NetworkSource.splitHost(host)
  return copy(
    host = parts.host.ifBlank { host },
    port = parts.port ?: port,
    basePath = basePath.ifBlank { parts.basePath ?: "" },
  )
}

private fun NetworkDraft.toNetworkSource(id: Long): NetworkSource = NetworkSource(
  id = id,
  type = type,
  name = name,
  host = host,
  port = port,
  basePath = basePath,
  secure = secure,
  username = username,
  encodedPassword = NetworkSource.encodePassword(password),
)

/**
 * An empty password means "keep the current one": the dialog never echoes
 * the stored secret back in plain text.
 */
private fun NetworkSource.updatedWith(draft: NetworkDraft): NetworkSource = NetworkSource(
  id = id,
  type = draft.type,
  name = draft.name,
  host = draft.host,
  port = draft.port,
  basePath = draft.basePath,
  secure = draft.secure,
  username = draft.username,
  encodedPassword = draft.password.takeIf { it.isNotEmpty() }
    ?.let(NetworkSource::encodePassword)
    ?: encodedPassword,
)
