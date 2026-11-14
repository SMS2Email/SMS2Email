package io.github.sms2email.sms2email.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.sms2email.sms2email.AboutActivity
import io.github.sms2email.sms2email.SmtpEncryptionMode
import io.github.sms2email.sms2email.preferences.AppConfig
import io.github.sms2email.sms2email.preferences.AvailableSim
import io.github.sms2email.sms2email.preferences.EmailPreference
import io.github.sms2email.sms2email.preferences.MatrixPreference
import io.github.sms2email.sms2email.preferences.MatrixRoomOption
import io.github.sms2email.sms2email.preferences.PreferencesManager
import io.github.sms2email.sms2email.preferences.SimInput
import io.github.sms2email.sms2email.preferences.activeSimCards
import io.github.sms2email.sms2email.preferences.newEmailPreference
import io.github.sms2email.sms2email.preferences.newMatrixPreference
import io.github.sms2email.sms2email.send.SmsEvent
import io.github.sms2email.sms2email.send.sendSms
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    context: Context,
    isSmsPermissionGranted: Boolean,
    onRequestSmsPermission: () -> Unit,
    isPhonePermissionGranted: Boolean,
    onRequestPhonePermission: () -> Unit,
    isNotificationPermissionGranted: Boolean,
    onRequestNotificationPermission: () -> Unit,
    matrixRooms: Map<String, List<MatrixRoomOption>> = emptyMap(),
    loadingMatrix: Set<String> = emptySet(),
    onConnectMatrix: (MatrixPreference) -> Unit = {},
    onSelectMatrixRoom: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
  val config by PreferencesManager.configFlow(context).collectAsState(initial = AppConfig())
  val scope = rememberCoroutineScope()
  val availableSims =
      remember(isPhonePermissionGranted) {
        if (isPhonePermissionGranted) activeSimCards(context) else emptyList()
      }

  LaunchedEffect(availableSims) { PreferencesManager.syncInputs(context, availableSims) }

  Column(
      modifier = modifier.systemBarsPadding().padding(16.dp).verticalScroll(rememberScrollState()),
  ) {
    Text(
        text = "SMS2Email",
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(bottom = 16.dp),
    )
    Text(
        text = "Forward SMS from selected SIMs to one or more destinations.",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(bottom = 16.dp),
    )

    PermissionCard(
        title = "SMS permission",
        granted = isSmsPermissionGranted,
        action = "Grant SMS permission",
        onRequest = onRequestSmsPermission,
    )
    PermissionCard(
        title = "SIM identification permission",
        granted = isPhonePermissionGranted,
        action = "Grant phone permission",
        description = "Only used to identify active SIM slots; phone numbers are not read.",
        onRequest = onRequestPhonePermission,
    )
    PermissionCard(
        title = "Notifications",
        granted = isNotificationPermissionGranted,
        action = "Grant notification permission",
        description = "Used to report delivery success and failure.",
        onRequest = onRequestNotificationPermission,
    )

    SectionTitle("SMS inputs")
    SimInputsCard(
        context = context,
        config = config,
        availableSims = availableSims,
        onUpdate = { input -> scope.launch { PreferencesManager.updateInput(context, input) } },
    )

    SectionTitle("Email senders")
    EmailCards(
        emails = config.emails,
        onUpdate = { id, update ->
          scope.launch { PreferencesManager.updateEmail(context, id, update) }
        },
        onAdd = { scope.launch { PreferencesManager.addEmail(context, newEmailPreference()) } },
        onRemove = { id -> scope.launch { PreferencesManager.removeEmail(context, id) } },
    )

    SectionTitle("Matrix senders")
    MatrixCards(
        matrix = config.matrix,
        rooms = matrixRooms,
        loadingMatrix = loadingMatrix,
        onUpdate = { id, update ->
          scope.launch { PreferencesManager.updateMatrix(context, id, update) }
        },
        onConnect = onConnectMatrix,
        onSelectRoom = onSelectMatrixRoom,
        onAdd = { scope.launch { PreferencesManager.addMatrix(context, newMatrixPreference()) } },
        onRemove = { id -> scope.launch { PreferencesManager.removeMatrix(context, id) } },
    )

    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = {
          scope.launch {
            val report =
                sendSms(
                    context,
                    SmsEvent.create("[TEST]", "This is a test SMS."),
                    bypassInputFilter = true,
                )
            val message =
                if (report == 0) "No sender succeeded" else "Test sent to $report sender(s)"
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT)
                .show()
          }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
      Text("Send test SMS")
    }

    Text(
        text = "Licenses...",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
        textDecoration = TextDecoration.Underline,
        modifier =
            Modifier.padding(top = 20.dp, bottom = 16.dp).clickable {
              context.startActivity(Intent(context, AboutActivity::class.java))
            },
    )
  }
}

@Composable
private fun PermissionCard(
    title: String,
    granted: Boolean,
    action: String,
    onRequest: () -> Unit,
    description: String? = null,
) {
  Card(
      modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
      colors =
          CardDefaults.cardColors(
              containerColor =
                  if (granted) MaterialTheme.colorScheme.primaryContainer
                  else MaterialTheme.colorScheme.errorContainer,
          ),
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Text(
          text = if (granted) "✓ $title" else "✗ $title",
          style = MaterialTheme.typography.titleSmall)
      if (!granted) {
        description?.let {
          Text(
              it,
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.padding(top = 4.dp))
        }
        Button(onClick = onRequest, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
          Text(action)
        }
      }
    }
  }
}

@Composable
private fun SectionTitle(title: String) {
  Text(
      text = title,
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
  )
}

@Composable
private fun SimInputsCard(
    context: Context,
    config: AppConfig,
    availableSims: List<AvailableSim>,
    onUpdate: (SimInput) -> Unit,
) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(12.dp)) {
      Text(
          text =
              if (!config.inputsSelected) {
                "All SIMs are accepted until individual SIMs are configured."
              } else {
                "Only enabled SIMs below trigger forwarding."
              },
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.padding(bottom = 8.dp),
      )
      when {
        !context.isSimPermissionAvailable() ->
            Text(
                "Grant SIM identification permission to choose individual SIMs.",
                style = MaterialTheme.typography.bodySmall)
        config.inputs.isEmpty() && availableSims.isEmpty() ->
            Text("No active SIMs found.", style = MaterialTheme.typography.bodySmall)
        config.inputs.isEmpty() ->
            Text("Loading active SIMs...", style = MaterialTheme.typography.bodySmall)
        else ->
            config.inputs
                .sortedBy { it.slotIndex }
                .forEachIndexed { index, input ->
                  if (index > 0) HorizontalDivider()
                  Row(
                      modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                      verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(input.label, style = MaterialTheme.typography.bodyLarge)
                      Text(
                          "SIM slot ${input.slotIndex + 1}",
                          style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = input.enabled,
                        onCheckedChange = { onUpdate(input.copy(enabled = it)) })
                  }
                }
      }
    }
  }
}

@Composable
private fun EmailCards(
    emails: List<EmailPreference>,
    onUpdate: (String, (EmailPreference) -> EmailPreference) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
  emails.forEach { email ->
    key(email.id) {
      val label =
          rememberPreferenceTextState(email.label) { value ->
            onUpdate(email.id) { it.copy(label = value) }
          }
      SenderCard(
          id = email.id,
          label = label.value.ifBlank { "Email" },
          enabled = email.enabled,
          incomplete = !email.isConfigured(),
          onEnabled = { enabled ->
            onUpdate(email.id) { current -> current.copy(enabled = enabled) }
          },
          onRemove = { onRemove(email.id) },
      ) {
        TextField(label, "Name") { label.value = it }
        EmailFields(email, onUpdate)
      }
    }
  }
  AddButton("Add email sender", onAdd)
}

@Composable
private fun MatrixCards(
    matrix: List<MatrixPreference>,
    rooms: Map<String, List<MatrixRoomOption>>,
    loadingMatrix: Set<String>,
    onUpdate: (String, (MatrixPreference) -> MatrixPreference) -> Unit,
    onConnect: (MatrixPreference) -> Unit,
    onSelectRoom: (String, String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
  matrix.forEach { item ->
    key(item.id) {
      val label =
          rememberPreferenceTextState(item.label) { value ->
            onUpdate(item.id) { it.copy(label = value) }
          }
      SenderCard(
          id = item.id,
          label = label.value.ifBlank { "Matrix" },
          enabled = item.enabled,
          incomplete = !item.isConfigured(),
          onEnabled = { enabled ->
            onUpdate(item.id) { current -> current.copy(enabled = enabled) }
          },
          onRemove = { onRemove(item.id) },
      ) {
        TextField(label, "Name") { label.value = it }
        MatrixFields(
            matrix = item,
            rooms = rooms[item.id].orEmpty(),
            loading = item.id in loadingMatrix,
            onUpdate = onUpdate,
            onConnect = { onConnect(item) },
            onSelectRoom = { roomId -> onSelectRoom(item.id, roomId) },
        )
      }
    }
  }
  AddButton("Add Matrix sender", onAdd)
}

@Composable
private fun SenderCard(
    id: String,
    label: String,
    enabled: Boolean,
    incomplete: Boolean,
    onEnabled: (Boolean) -> Unit,
    onRemove: () -> Unit,
    content: @Composable () -> Unit,
) {
  var expanded by rememberSaveable(id) { mutableStateOf(false) }
  Card(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = enabled, onCheckedChange = onEnabled)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
          Text(label, style = MaterialTheme.typography.titleMedium)
          if (enabled && incomplete) {
            Text(
                "Incomplete configuration",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
          }
        }
        OutlinedButton(onClick = { expanded = !expanded }) {
          Text(if (expanded) "Hide" else "Edit")
        }
      }
      if (expanded) {
        Spacer(modifier = Modifier.height(8.dp))
        content()
        OutlinedButton(onClick = onRemove, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
          Text("Remove sender")
        }
      }
    }
  }
}

@Composable
private fun AddButton(label: String, onClick: () -> Unit) {
  OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
    Text(label)
  }
}

@Composable
private fun TextField(state: MutableState<String>, label: String, onValueChange: (String) -> Unit) {
  OutlinedTextField(
      value = state.value,
      onValueChange = onValueChange,
      label = { Text(label) },
      singleLine = true,
      modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
  )
}

@Composable
private fun EmailFields(
    email: EmailPreference,
    onUpdate: (String, (EmailPreference) -> EmailPreference) -> Unit,
) {
  val recipient =
      rememberPreferenceTextState(email.emailTo) { value ->
        onUpdate(email.id) { it.copy(emailTo = value) }
      }
  val host =
      rememberPreferenceTextState(email.smtpHost) { value ->
        onUpdate(email.id) { it.copy(smtpHost = value) }
      }
  val port =
      rememberPreferenceTextState(email.smtpPort.toString()) { value ->
        value
            .toIntOrNull()
            ?.takeIf { it > 0 }
            ?.let { onUpdate(email.id) { current -> current.copy(smtpPort = it) } }
      }
  val user =
      rememberPreferenceTextState(email.smtpUser) { value ->
        onUpdate(email.id) { it.copy(smtpUser = value) }
      }
  val token =
      rememberPreferenceTextState(email.smtpToken) { value ->
        onUpdate(email.id) { it.copy(smtpToken = value) }
      }
  val from =
      rememberPreferenceTextState(email.fromEmail) { value ->
        onUpdate(email.id) { it.copy(fromEmail = value) }
      }

  TextField(recipient, "Recipient email") { recipient.value = it }
  TextField(host, "SMTP host") { host.value = it }
  TextField(port, "SMTP port") { port.value = it }
  SelectionMenu(
      label = "Encryption",
      value = email.encryptionMode.label(),
      options =
          listOf(
              SmtpEncryptionMode.SMTP_ENCRYPTION_MODE_STARTTLS,
              SmtpEncryptionMode.SMTP_ENCRYPTION_MODE_SMTPS,
              SmtpEncryptionMode.SMTP_ENCRYPTION_MODE_NONE,
          ),
      optionLabel = { it.label() },
      onSelect = { mode -> onUpdate(email.id) { it.copy(encryptionMode = mode) } },
  )
  Spacer(modifier = Modifier.height(8.dp))
  TextField(user, "SMTP username") { user.value = it }
  OutlinedTextField(
      value = token.value,
      onValueChange = { token.value = it },
      label = { Text("SMTP OAuth token") },
      singleLine = true,
      visualTransformation = PasswordVisualTransformation(),
      modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
  )
  TextField(from, "From email") { from.value = it }
}

@Composable
private fun MatrixFields(
    matrix: MatrixPreference,
    rooms: List<MatrixRoomOption>,
    loading: Boolean,
    onUpdate: (String, (MatrixPreference) -> MatrixPreference) -> Unit,
    onConnect: () -> Unit,
    onSelectRoom: (String) -> Unit,
) {
  val homeserver =
      rememberPreferenceTextState(matrix.homeserver) { value ->
        onUpdate(matrix.id) { it.copy(homeserver = value) }
      }
  TextField(homeserver, "Homeserver HTTPS URL") { homeserver.value = it }
  if (loading) {
    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
    Text(
        "Loading joined rooms...",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(bottom = 8.dp),
    )
  }
  Button(
      enabled = !loading,
      onClick = onConnect,
      modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
  ) {
    Text(
        if (loading) "Loading rooms..."
        else if (matrix.user.isBlank()) "Sign in with Matrix" else "Reconnect Matrix")
  }
  if (matrix.user.isNotBlank()) {
    Text(
        text = "Connected as ${matrix.user}",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(bottom = 8.dp),
    )
  }
  if (rooms.isNotEmpty()) {
    val selectedRoom = rooms.firstOrNull { it.id == matrix.roomId }
    SelectionMenu(
        label = "Room",
        value = selectedRoom?.name ?: "Select a room",
        options = rooms,
        optionLabel = { it.name },
        onSelect = { onSelectRoom(it.id) },
    )
  } else if (matrix.user.isNotBlank()) {
    Text("No joined rooms found.", style = MaterialTheme.typography.bodySmall)
  }
}

@Composable
private fun <T> SelectionMenu(
    label: String,
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
  var expanded by rememberSaveable { mutableStateOf(false) }
  Box(modifier = Modifier.fillMaxWidth()) {
    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value)
      }
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        modifier = Modifier.fillMaxWidth(0.9f)) {
          options.forEach { option ->
            DropdownMenuItem(
                text = { Text(optionLabel(option)) },
                onClick = {
                  expanded = false
                  onSelect(option)
                },
            )
          }
        }
  }
}

@Composable
private fun rememberPreferenceTextState(
    storedValue: String,
    debounceMillis: Long = 500L,
    onPersist: suspend (String) -> Unit,
): MutableState<String> {
  val inputState = rememberSaveable { mutableStateOf(storedValue) }
  LaunchedEffect(storedValue) {
    if (storedValue != inputState.value) inputState.value = storedValue
  }
  LaunchedEffect(inputState.value, storedValue) {
    val currentValue = inputState.value
    if (currentValue == storedValue) return@LaunchedEffect
    delay(debounceMillis)
    if (currentValue == inputState.value && currentValue != storedValue) onPersist(currentValue)
  }
  return inputState
}

private fun SmtpEncryptionMode.label(): String =
    when (this) {
      SmtpEncryptionMode.SMTP_ENCRYPTION_MODE_SMTPS -> "SMTPS (SSL/TLS)"
      SmtpEncryptionMode.SMTP_ENCRYPTION_MODE_NONE -> "None"
      else -> "STARTTLS"
    }

private fun Context.isSimPermissionAvailable(): Boolean =
    androidx.core.content.ContextCompat.checkSelfPermission(
        this,
        android.Manifest.permission.READ_PHONE_STATE,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
