package io.github.sms2email.sms2email.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.sms2email.sms2email.AboutActivity
import io.github.sms2email.sms2email.PgpMode
import io.github.sms2email.sms2email.SmtpEncryptionMode
import io.github.sms2email.sms2email.preferences.AppConfig
import io.github.sms2email.sms2email.preferences.AvailableSim
import io.github.sms2email.sms2email.preferences.EmailPreference
import io.github.sms2email.sms2email.preferences.MatrixPreference
import io.github.sms2email.sms2email.preferences.MatrixRoomOption
import io.github.sms2email.sms2email.preferences.SimInput
import io.github.sms2email.sms2email.preferences.activeSimCards
import io.github.sms2email.sms2email.preferences.addEmail
import io.github.sms2email.sms2email.preferences.addMatrix
import io.github.sms2email.sms2email.preferences.configFlow
import io.github.sms2email.sms2email.preferences.newEmailPreference
import io.github.sms2email.sms2email.preferences.newMatrixPreference
import io.github.sms2email.sms2email.preferences.removeEmail
import io.github.sms2email.sms2email.preferences.removeMatrix
import io.github.sms2email.sms2email.preferences.syncInputs
import io.github.sms2email.sms2email.preferences.updateEmail
import io.github.sms2email.sms2email.preferences.updateMatrix
import io.github.sms2email.sms2email.send.ENCRYPTED_SUBJECT
import io.github.sms2email.sms2email.send.OPENKEYCHAIN_PACKAGE
import io.github.sms2email.sms2email.send.OpenKeychainResult
import io.github.sms2email.sms2email.send.SmsEvent
import io.github.sms2email.sms2email.send.formatKeyId
import io.github.sms2email.sms2email.send.isOpenKeychainInstalled
import io.github.sms2email.sms2email.send.secretMimeEntity
import io.github.sms2email.sms2email.send.selectRecipientKeys
import io.github.sms2email.sms2email.send.selectSigningKey
import io.github.sms2email.sms2email.send.signAndEncrypt
import io.github.sms2email.sms2email.send.sendSms
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val settingsSpacing = 12.dp

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
  val config by configFlow(context).collectAsState(initial = AppConfig())
  val scope = rememberCoroutineScope()
  val availableSims =
      remember(isPhonePermissionGranted) {
        if (isPhonePermissionGranted) activeSimCards(context) else emptyList()
      }

  LaunchedEffect(
      availableSims,
      config.emails.map { it.id },
      config.matrix.map { it.id },
  ) {
    syncInputs(context, availableSims)
  }

  Column(
      modifier = modifier.systemBarsPadding().padding(16.dp).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(settingsSpacing),
  ) {
    Text(
        text = "SMS2Email",
        style = MaterialTheme.typography.headlineSmall,
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
      Text(
          text =
              "SMS2Email automatically forwards received SMS to configured outputs, even if the app is closed.",
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.padding(12.dp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    PermissionCard(
        title = "SMS permission",
        granted = isSmsPermissionGranted,
        action = "Grant SMS permission",
        onRequest = onRequestSmsPermission,
    )
    PermissionCard(
        title = "SIM identification permission",
        granted = isPhonePermissionGranted,
        required = false,
        action = "Grant phone permission",
        description = "Only used to identify active SIM slots; phone numbers are not read.",
        onRequest = onRequestPhonePermission,
    )
    PermissionCard(
        title = "Notifications",
        granted = isNotificationPermissionGranted,
        required = false,
        action = "Grant notification permission",
        description = "Used to report delivery success and failure.",
        onRequest = onRequestNotificationPermission,
    )

    EmailCards(
        context = context,
        emails = config.emails,
        availableSims = availableSims,
        onUpdate = { id, update -> scope.launch { updateEmail(context, id, update) } },
        onRemove = { id -> scope.launch { removeEmail(context, id) } },
    )

    MatrixCards(
        matrix = config.matrix,
        availableSims = availableSims,
        rooms = matrixRooms,
        loadingMatrix = loadingMatrix,
        onUpdate = { id, update -> scope.launch { updateMatrix(context, id, update) } },
        onConnect = onConnectMatrix,
        onSelectRoom = onSelectMatrixRoom,
        onRemove = { id -> scope.launch { removeMatrix(context, id) } },
    )

    AddOutputMenu(
        onAddEmail = { scope.launch { addEmail(context, newEmailPreference()) } },
        onAddMatrix = { scope.launch { addMatrix(context, newMatrixPreference()) } },
    )

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
      Text("Test")
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
      Text(
          text = "Licenses...",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurface,
          textDecoration = TextDecoration.Underline,
          modifier =
              Modifier.clickable {
                context.startActivity(Intent(context, AboutActivity::class.java))
              },
      )
    }
  }
}

@Composable
private fun PermissionCard(
    title: String,
    granted: Boolean,
    required: Boolean = true,
    action: String,
    onRequest: () -> Unit,
    description: String? = null,
) {
  Card(
      modifier = Modifier.fillMaxWidth(),
      colors =
          CardDefaults.cardColors(
              containerColor =
                  when {
                    granted -> MaterialTheme.colorScheme.primaryContainer
                    required -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                  },
          ),
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Text(
          text =
              when {
                granted -> "✓ $title"
                required -> "✗ $title"
                else -> "$title (optional)"
              },
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
private fun OutputSimFields(
    inputs: List<SimInput>,
    availableSims: List<AvailableSim>,
    onUpdate: (List<SimInput>) -> Unit,
) {
  if (availableSims.isEmpty()) return

  availableSims.forEachIndexed { index, sim ->
    val current = inputs.firstOrNull { it.slotIndex == sim.slotIndex }
    if (index > 0) HorizontalDivider()
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(sim.label, style = MaterialTheme.typography.bodyLarge)
        Text("SIM slot ${sim.slotIndex + 1}", style = MaterialTheme.typography.bodySmall)
      }
      Switch(
          checked = current?.enabled ?: true,
          onCheckedChange = { enabled ->
            onUpdate(
                availableSims.map { available ->
                  val old = inputs.firstOrNull { it.slotIndex == available.slotIndex }
                  SimInput(
                      id = old?.id ?: "slot-${available.slotIndex}",
                      slotIndex = available.slotIndex,
                      subscriptionId = available.subscriptionId,
                      label = available.label,
                      enabled =
                          if (available.slotIndex == sim.slotIndex) enabled
                          else old?.enabled ?: true,
                  )
                },
            )
          },
      )
    }
  }
}

@Composable
private fun EmailCards(
    context: Context,
    emails: List<EmailPreference>,
    availableSims: List<AvailableSim>,
    onUpdate: (String, (EmailPreference) -> EmailPreference) -> Unit,
    onRemove: (String) -> Unit,
) {
  emails.forEachIndexed { index, email ->
    key(email.id) {
      SenderCard(
          id = email.id,
          label = "SMTP ${index + 1}",
          enabled = email.enabled,
          incomplete = !email.isConfigured(),
          onEnabled = { enabled ->
            onUpdate(email.id) { current -> current.copy(enabled = enabled) }
          },
          onRemove = { onRemove(email.id) },
      ) {
        EmailFields(context, email, availableSims, onUpdate)
      }
    }
  }
}

@Composable
private fun MatrixCards(
    matrix: List<MatrixPreference>,
    availableSims: List<AvailableSim>,
    rooms: Map<String, List<MatrixRoomOption>>,
    loadingMatrix: Set<String>,
    onUpdate: (String, (MatrixPreference) -> MatrixPreference) -> Unit,
    onConnect: (MatrixPreference) -> Unit,
    onSelectRoom: (String, String) -> Unit,
    onRemove: (String) -> Unit,
) {
  matrix.forEachIndexed { index, item ->
    key(item.id) {
      SenderCard(
          id = item.id,
          label = "Matrix ${index + 1}",
          enabled = item.enabled,
          incomplete = !item.isConfigured(),
          onEnabled = { enabled ->
            onUpdate(item.id) { current -> current.copy(enabled = enabled) }
          },
          onRemove = { onRemove(item.id) },
      ) {
        MatrixFields(
            matrix = item,
            availableSims = availableSims,
            rooms = rooms[item.id].orEmpty(),
            loading = item.id in loadingMatrix,
            onUpdate = onUpdate,
            onConnect = { onConnect(item) },
            onSelectRoom = { roomId -> onSelectRoom(item.id, roomId) },
        )
      }
    }
  }
}

@Composable
private fun AddOutputMenu(
    onAddEmail: () -> Unit,
    onAddMatrix: () -> Unit,
) {
  var expanded by rememberSaveable { mutableStateOf(false) }
  Box(modifier = Modifier.fillMaxWidth()) {
    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Add...")
        Text("⌄")
      }
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        modifier = Modifier.fillMaxWidth(0.9f),
    ) {
      DropdownMenuItem(
          text = { Text("Email sender") },
          onClick = {
            expanded = false
            onAddEmail()
          },
      )
      DropdownMenuItem(
          text = { Text("Matrix sender") },
          onClick = {
            expanded = false
            onAddMatrix()
          },
      )
    }
  }
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
  Card(modifier = Modifier.fillMaxWidth().animateContentSize()) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = enabled, onCheckedChange = onEnabled)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
          Text(label, style = MaterialTheme.typography.titleMedium)
          if (incomplete) {
            Text(
                "Incomplete configuration",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
          }
        }
        IconButton(onClick = onRemove) {
          Icon(
              Icons.Default.Delete,
              contentDescription = "Discard $label",
              tint = MaterialTheme.colorScheme.error,
          )
        }
        IconButton(onClick = { expanded = !expanded }) {
          Icon(
              imageVector = if (expanded) Icons.Default.ChevronLeft else Icons.Default.ChevronRight,
              contentDescription = if (expanded) "Collapse $label" else "Expand $label",
              modifier = Modifier.rotate(90f),
          )
        }
      }
      if (expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(settingsSpacing)) { content() }
      }
    }
  }
}

@Composable
private fun TextField(
    state: MutableState<String>,
    label: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions(),
    onValueChange: (String) -> Unit,
) {
  OutlinedTextField(
      value = state.value,
      onValueChange = onValueChange,
      label = { Text(label) },
      singleLine = true,
      keyboardOptions = keyboardOptions,
      modifier = Modifier.fillMaxWidth(),
  )
}

@Composable
private fun EmailFields(
    context: Context,
    email: EmailPreference,
    availableSims: List<AvailableSim>,
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
        value.toIntOrNull()?.let { onUpdate(email.id) { current -> current.copy(smtpPort = it) } }
      }
  val user =
      rememberPreferenceTextState(email.smtpUser) { value ->
        onUpdate(email.id) { it.copy(smtpUser = value) }
      }
  val password =
      rememberPreferenceTextState(email.smtpPassword) { value ->
        onUpdate(email.id) { it.copy(smtpPassword = value) }
      }
  val from =
      rememberPreferenceTextState(email.fromEmail) { value ->
        onUpdate(email.id) { it.copy(fromEmail = value) }
      }

  val portNumber = port.value.toIntOrNull()
  val warningText =
      when {
        email.encryptionMode == SmtpEncryptionMode.SMTP_ENCRYPTION_MODE_NONE ->
            "Warning: No encryption selected. This may expose credentials and email content."
        portNumber == 465 &&
            email.encryptionMode != SmtpEncryptionMode.SMTP_ENCRYPTION_MODE_SMTPS ->
            "Warning: Port 465 is typically used with SMTPS (SSL/TLS)."
        portNumber != null &&
            portNumber != 465 &&
            email.encryptionMode == SmtpEncryptionMode.SMTP_ENCRYPTION_MODE_SMTPS ->
            "Warning: SMTPS (SSL/TLS) typically uses port 465."
        else -> null
      }

  OutputSimFields(
      inputs = email.inputs,
      availableSims = availableSims,
      onUpdate = { inputs -> onUpdate(email.id) { it.copy(inputs = inputs) } },
  )
  TextField(host, "SMTP Host") { host.value = it }
  TextField(
      port,
      "SMTP Port",
      onValueChange = { port.value = it },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
  )
  EncryptionMenu(
      value = email.encryptionMode,
      onSelect = { mode -> onUpdate(email.id) { it.copy(encryptionMode = mode) } },
  )
  if (warningText != null) {
    Text(
        text = warningText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
  }
  TextField(user, "SMTP Username") { user.value = it }
  if (email.smtpHost.contains("gmail", ignoreCase = true) &&
      user.value.isNotBlank() &&
      user.value != from.value) {
    Text(
        text =
            "Warning: For Gmail, the SMTP username should be your full email address (same as From Email Address).",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
  }
  OutlinedTextField(
      value = password.value,
      onValueChange = { password.value = it },
      label = { Text("SMTP Password") },
      singleLine = true,
      visualTransformation = PasswordVisualTransformation(),
      modifier = Modifier.fillMaxWidth(),
  )
  if (email.smtpHost.contains("gmail", ignoreCase = true) &&
      password.value.replace(" ", "").length != 16) {
    Text(
        text =
            "Your 16-digit Google \"App password\" needs to be entered, not your Google Account password.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
    Text(
        text = "Generate App Password",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        modifier =
            Modifier.clickable {
              context.startActivity(
                  Intent(
                      Intent.ACTION_VIEW, Uri.parse("https://myaccount.google.com/apppasswords")))
            },
    )
  }
  TextField(
      from,
      "From Email Address",
      onValueChange = { from.value = it },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
  )
  TextField(
      recipient,
      "To Email Address",
      onValueChange = { recipient.value = it },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
  )
  PgpFields(context, email, onUpdate)
}

@Composable
private fun PgpFields(
    context: Context,
    email: EmailPreference,
    onUpdate: (String, (EmailPreference) -> EmailPreference) -> Unit,
) {
  val enabled = email.pgpMode == PgpMode.PGP_MODE_OPENKEYCHAIN
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Column(modifier = Modifier.weight(1f)) {
      Text("Sign and encrypt with OpenKeychain", style = MaterialTheme.typography.bodyLarge)
      Text(
          "OpenPGP end-to-end encryption, decrypted and verified by Thunderbird",
          style = MaterialTheme.typography.bodySmall,
      )
    }
    Switch(
        checked = enabled,
        onCheckedChange = { on ->
          onUpdate(email.id) {
            it.copy(pgpMode = if (on) PgpMode.PGP_MODE_OPENKEYCHAIN else PgpMode.PGP_MODE_OFF)
          }
        },
    )
  }
  if (enabled) OpenKeychainFields(context, email, onUpdate)
}

private enum class OpenKeychainStep {
  RECIPIENT,
  SIGNER,
  CHECK,
}

@Composable
private fun OpenKeychainFields(
    context: Context,
    email: EmailPreference,
    onUpdate: (String, (EmailPreference) -> EmailPreference) -> Unit,
) {
  val scope = rememberCoroutineScope()
  val installed = remember { isOpenKeychainInstalled(context) }
  val currentEmail by rememberUpdatedState(email)
  var status by remember { mutableStateOf<String?>(null) }
  var statusIsError by remember { mutableStateOf(true) }
  var pendingStep by remember { mutableStateOf<OpenKeychainStep?>(null) }
  lateinit var launcher: ActivityResultLauncher<IntentSenderRequest>

  fun <T> handle(step: OpenKeychainStep, result: OpenKeychainResult<T>, onDone: (T) -> Unit) {
    when (result) {
      is OpenKeychainResult.Done -> onDone(result.value)
      is OpenKeychainResult.NeedsUser -> {
        pendingStep = step
        launcher.launch(IntentSenderRequest.Builder(result.pendingIntent.intentSender).build())
      }
    }
  }

  fun attempt(step: OpenKeychainStep, previous: Intent? = null) {
    status = null
    scope.launch {
      val target = currentEmail
      runCatching {
            when (step) {
              OpenKeychainStep.RECIPIENT ->
                  handle(step, selectRecipientKeys(context, previous)) { ids ->
                    onUpdate(target.id) { it.copy(pgpKeyIds = ids) }
                  }
              OpenKeychainStep.SIGNER ->
                  handle(step, selectSigningKey(context, previous)) { id ->
                    onUpdate(target.id) { it.copy(pgpSignKeyId = id) }
                  }
              OpenKeychainStep.CHECK ->
                  handle(
                      step,
                      signAndEncrypt(
                          context,
                          secretMimeEntity(
                              SmsEvent.create("[TEST]", "Encryption check"), target.inputs),
                          target.pgpKeyIds,
                          target.pgpSignKeyId,
                          previous,
                      ),
                  ) {
                    status = "Signing and encryption work."
                    statusIsError = false
                  }
            }
          }
          .onFailure {
            status = it.message
            statusIsError = true
          }
    }
  }

  launcher =
      rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
          result ->
        val step = pendingStep ?: return@rememberLauncherForActivityResult
        pendingStep = null
        val data = result.data
        if (result.resultCode != android.app.Activity.RESULT_OK || data == null) {
          status = "Cancelled in OpenKeychain."
          statusIsError = true
          return@rememberLauncherForActivityResult
        }
        attempt(step, data)
      }

  if (!installed) {
    Text(
        text = "OpenKeychain is not installed. Install it from F-Droid, then come back.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
    Text(
        text = "Get OpenKeychain",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        modifier =
            Modifier.clickable {
              context.startActivity(
                  Intent(
                      Intent.ACTION_VIEW,
                      Uri.parse("https://f-droid.org/packages/$OPENKEYCHAIN_PACKAGE/")))
            },
    )
    return
  }

  KeyLine(
      label = "Recipient key",
      value = email.pgpKeyIds.takeIf { it.isNotEmpty() }?.joinToString { formatKeyId(it) },
  )
  OutlinedButton(
      onClick = { attempt(OpenKeychainStep.RECIPIENT) },
      modifier = Modifier.fillMaxWidth(),
  ) {
    Text(if (email.pgpKeyIds.isEmpty()) "Choose recipient key" else "Change recipient key")
  }
  KeyLine(
      label = "Signing key (this phone)",
      value = email.pgpSignKeyId.takeIf { it != 0L }?.let { formatKeyId(it) },
  )
  OutlinedButton(
      onClick = { attempt(OpenKeychainStep.SIGNER) },
      modifier = Modifier.fillMaxWidth(),
  ) {
    Text(if (email.pgpSignKeyId == 0L) "Choose signing key" else "Change signing key")
  }
  Button(
      onClick = { attempt(OpenKeychainStep.CHECK) },
      enabled = email.isPgpConfigured(),
      modifier = Modifier.fillMaxWidth(),
  ) {
    Text("Check encryption")
  }
  status?.let {
    Text(
        text = it,
        style = MaterialTheme.typography.bodySmall,
        color =
            if (statusIsError) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.primary,
    )
  }
  Text(
      text =
          "The subject is always \"$ENCRYPTED_SUBJECT\"; the sender, time and text are only in " +
              "the encrypted part. If OpenKeychain cannot sign and encrypt, nothing is sent. " +
              "If the signing key has a passphrase, OpenKeychain only remembers it for a while; " +
              "after that SMS are not sent until you tap \"Check encryption\" again. A key made " +
              "just for this phone, without a passphrase, avoids this.",
      style = MaterialTheme.typography.bodySmall,
  )
}

@Composable
private fun KeyLine(label: String, value: String?) {
  Text(
      text = "$label: ${value ?: "not chosen"}",
      style = MaterialTheme.typography.bodySmall,
      color =
          if (value == null) MaterialTheme.colorScheme.error
          else MaterialTheme.colorScheme.onSurface,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EncryptionMenu(
    value: SmtpEncryptionMode,
    onSelect: (SmtpEncryptionMode) -> Unit,
) {
  var expanded by rememberSaveable { mutableStateOf(false) }
  ExposedDropdownMenuBox(
      expanded = expanded,
      onExpandedChange = { expanded = !expanded },
      modifier = Modifier.fillMaxWidth(),
  ) {
    OutlinedTextField(
        value = value.label(),
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        label = { Text("Encryption") },
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        modifier =
            Modifier.menuAnchor(
                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = true,
                )
                .fillMaxWidth(),
    )
    ExposedDropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
      listOf(
              SmtpEncryptionMode.SMTP_ENCRYPTION_MODE_STARTTLS,
              SmtpEncryptionMode.SMTP_ENCRYPTION_MODE_SMTPS,
              SmtpEncryptionMode.SMTP_ENCRYPTION_MODE_NONE,
          )
          .forEach { mode ->
            DropdownMenuItem(
                text = { Text(mode.label()) },
                onClick = {
                  expanded = false
                  onSelect(mode)
                },
            )
          }
    }
  }
}

@Composable
private fun MatrixFields(
    matrix: MatrixPreference,
    availableSims: List<AvailableSim>,
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
  OutputSimFields(
      inputs = matrix.inputs,
      availableSims = availableSims,
      onUpdate = { inputs -> onUpdate(matrix.id) { it.copy(inputs = inputs) } },
  )
  TextField(homeserver, "Homeserver HTTPS URL") { homeserver.value = it }
  if (loading) {
    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    Text("Loading joined rooms...", style = MaterialTheme.typography.bodySmall)
  }
  Button(
      enabled = !loading,
      onClick = onConnect,
      modifier = Modifier.fillMaxWidth(),
  ) {
    Text(
        if (loading) "Loading rooms..."
        else if (matrix.user.isBlank()) "Sign in with Matrix" else "Reconnect Matrix")
  }
  if (matrix.user.isNotBlank()) {
    Text(
        text = "Connected as ${matrix.user}",
        style = MaterialTheme.typography.bodySmall,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SelectionMenu(
    label: String,
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
  var expanded by rememberSaveable { mutableStateOf(false) }
  ExposedDropdownMenuBox(
      expanded = expanded,
      onExpandedChange = { expanded = !expanded },
      modifier = Modifier.fillMaxWidth(),
  ) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        label = { Text(label) },
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        modifier =
            Modifier.menuAnchor(
                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = true,
                )
                .fillMaxWidth(),
    )
    ExposedDropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
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
