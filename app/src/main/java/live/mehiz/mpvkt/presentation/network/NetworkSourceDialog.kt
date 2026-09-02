package live.mehiz.mpvkt.presentation.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.network.NetworkSource
import live.mehiz.mpvkt.network.NetworkType
import live.mehiz.mpvkt.ui.theme.spacing

/**
 * Add/edit dialog for the network sources list. [initial] pre-fills the
 * fields for editing; the password is never echoed back in plain text —
 * leaving it empty keeps the stored one. The password is Base64-obfuscated
 * by the store, never encrypted (notice shown inline).
 */
@Composable
fun NetworkSourceDialog(
  title: String,
  initial: NetworkSource?,
  onConfirm: (draft: NetworkDraft) -> Unit,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var type by rememberSaveable { mutableStateOf(initial?.type ?: NetworkType.WEBDAV) }
  var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
  var host by rememberSaveable { mutableStateOf(initial?.host ?: "") }
  var port by rememberSaveable { mutableStateOf(initial?.port?.toString() ?: "") }
  var basePath by rememberSaveable { mutableStateOf(initial?.basePath ?: "") }
  var secure by rememberSaveable { mutableStateOf(initial?.secure ?: false) }
  var username by rememberSaveable { mutableStateOf(initial?.username ?: "") }
  var password by rememberSaveable { mutableStateOf("") }

  val defaultPort = if (type == NetworkType.WEBDAV) {
    if (secure) "443" else "80"
  } else {
    "21"
  }
  val valid = host.isNotBlank() && name.isNotBlank() && port.toIntOrNull() in 1..65535

  AlertDialog(
    onDismissRequest = onDismissRequest,
    modifier = modifier,
    title = { Text(title) },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
      ) {
        TypeSelector(type, onTypeChange = { type = it })
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text(stringResource(R.string.network_name)) },
          singleLine = true,
        )
        OutlinedTextField(
          value = host,
          onValueChange = { host = it },
          label = { Text(stringResource(R.string.network_host)) },
          singleLine = true,
        )
        OutlinedTextField(
          value = port,
          onValueChange = { port = it.filter(Char::isDigit) },
          label = { Text(stringResource(R.string.network_port)) },
          placeholder = { Text(defaultPort) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
        )
        OutlinedTextField(
          value = basePath,
          onValueChange = { basePath = it },
          label = { Text(stringResource(R.string.network_path)) },
          singleLine = true,
        )
        if (type == NetworkType.WEBDAV) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(stringResource(R.string.network_secure))
            Switch(checked = secure, onCheckedChange = { secure = it })
          }
        }
        OutlinedTextField(
          value = username,
          onValueChange = { username = it },
          label = { Text(stringResource(R.string.network_username)) },
          singleLine = true,
        )
        OutlinedTextField(
          value = password,
          onValueChange = { password = it },
          label = { Text(stringResource(R.string.network_password)) },
          placeholder = if (initial == null) {
            null
          } else {
            { Text(stringResource(R.string.network_password_keep)) }
          },
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
          singleLine = true,
        )
        Text(
          text = stringResource(R.string.network_password_notice),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          onConfirm(
            NetworkDraft(
              type = type,
              name = name.trim(),
              host = host.trim(),
              port = port.toInt(),
              basePath = basePath.trim(),
              secure = secure,
              username = username.trim(),
              password = password,
            ),
          )
        },
        enabled = valid,
      ) {
        Text(stringResource(R.string.generic_confirm))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismissRequest) {
        Text(stringResource(R.string.generic_cancel))
      }
    },
  )
}

/** The dialog output; [password] is plain text and empty means "keep it". */
data class NetworkDraft(
  val type: NetworkType,
  val name: String,
  val host: String,
  val port: Int,
  val basePath: String,
  val secure: Boolean,
  val username: String,
  val password: String,
)

@Composable
private fun TypeSelector(
  selected: NetworkType,
  onTypeChange: (NetworkType) -> Unit,
  modifier: Modifier = Modifier,
) {
  FlowRow(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
  ) {
    NetworkType.entries.forEach { candidate ->
      FilterChip(
        selected = candidate == selected,
        onClick = { onTypeChange(candidate) },
        label = { Text(candidate.label) },
      )
    }
  }
}

private val NetworkType.label: String
  get() = when (this) {
    NetworkType.WEBDAV -> "WebDAV"
    NetworkType.FTP -> "FTP"
  }
