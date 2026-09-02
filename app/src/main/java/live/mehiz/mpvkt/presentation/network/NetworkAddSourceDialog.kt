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
import live.mehiz.mpvkt.network.NetworkType
import live.mehiz.mpvkt.ui.theme.spacing

/**
 * Add-server dialog for the network sources list. The password is typed in
 * plain text here and immediately Base64-obfuscated by the store; it is
 * never encrypted (notice shown inline).
 */
@Composable
fun NetworkAddSourceDialog(
  onDismissRequest: () -> Unit,
  onAdd: (
    type: NetworkType,
    name: String,
    host: String,
    port: Int,
    basePath: String,
    secure: Boolean,
    username: String,
    password: String,
  ) -> Unit,
  modifier: Modifier = Modifier,
) {
  var type by rememberSaveable { mutableStateOf(NetworkType.WEBDAV) }
  var name by rememberSaveable { mutableStateOf("") }
  var host by rememberSaveable { mutableStateOf("") }
  var port by rememberSaveable { mutableStateOf("") }
  var basePath by rememberSaveable { mutableStateOf("") }
  var secure by rememberSaveable { mutableStateOf(false) }
  var username by rememberSaveable { mutableStateOf("") }
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
    title = { Text(stringResource(R.string.network_add_source)) },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
      ) {
        ExposedTypeSelector(type, onTypeChange = { type = it })
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
          onAdd(
            type,
            name.trim(),
            host.trim(),
            port.toInt(),
            basePath.trim(),
            secure,
            username.trim(),
            password,
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

@Composable
private fun ExposedTypeSelector(
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
