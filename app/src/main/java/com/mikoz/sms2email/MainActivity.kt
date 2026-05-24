package io.github.sms2email.sms2email

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.sms2email.sms2email.ui.theme.SMS2EmailTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  private val smsPermissionGrantedFlow = MutableStateFlow(false)
  private val notificationPermissionGrantedFlow = MutableStateFlow(false)
  private val phoneStatePermissionGrantedFlow = MutableStateFlow(false)
  private val requestPermissionLauncher =
      registerForActivityResult(
          ActivityResultContracts.RequestPermission(),
      ) { isGranted: Boolean ->
        if (!isGranted) {
          Toast.makeText(this, "SMS permission denied", Toast.LENGTH_SHORT).show()
        }
        smsPermissionGrantedFlow.value = isGranted
      }

  private val requestNotificationPermissionLauncher =
      registerForActivityResult(
          ActivityResultContracts.RequestPermission(),
      ) { isGranted: Boolean ->
        if (!isGranted) {
          Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
        }
        notificationPermissionGrantedFlow.value = isGranted
      }

  private val requestPhoneStatePermissionLauncher =
      registerForActivityResult(
          ActivityResultContracts.RequestPermission(),
      ) { isGranted: Boolean ->
        if (!isGranted) {
          Toast.makeText(this, "Phone state permission denied - dual SIM detection may not work", Toast.LENGTH_SHORT).show()
        }
        phoneStatePermissionGrantedFlow.value = isGranted
      }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // window.setBackgroundDrawableResource(R.drawable.background)
    enableEdgeToEdge()

    smsPermissionGrantedFlow.value = isSmsPermissionGranted()
    val initialSmsPermissionGranted = smsPermissionGrantedFlow.value

    notificationPermissionGrantedFlow.value = isNotificationPermissionGranted()
    val initialNotificationPermissionGranted = notificationPermissionGrantedFlow.value

    phoneStatePermissionGrantedFlow.value = isPhoneStatePermissionGranted()
    val initialPhoneStatePermissionGranted = phoneStatePermissionGrantedFlow.value

    setContent {
      SMS2EmailTheme {
        val isDark = isSystemInDarkTheme()
        val isSmsPermissionGranted by
            smsPermissionGrantedFlow.collectAsState(initial = initialSmsPermissionGranted)
        val isNotificationPermissionGranted by
            notificationPermissionGrantedFlow.collectAsState(
                initial = initialNotificationPermissionGranted,
            )
        val isPhoneStatePermissionGranted by
            phoneStatePermissionGrantedFlow.collectAsState(
                initial = initialPhoneStatePermissionGranted,
            )
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        ) {
          Box(modifier = Modifier.fillMaxSize()) {
            if (isDark) {
              Image(
                  painter = painterResource(id = R.drawable.background_dark),
                  contentDescription = "Background",
                  modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                  contentScale = ContentScale.FillWidth,
                  alpha = 0.3f,
              )
            }
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
            ) { innerPadding ->
              MailPreferencesScreen(
                  context = this@MainActivity,
                  isSmsPermissionGranted = isSmsPermissionGranted,
                  onRequestPermission = {
                    requestPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                  },
                  isNotificationPermissionGranted = isNotificationPermissionGranted,
                  onRequestNotificationPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                      requestNotificationPermissionLauncher.launch(
                          Manifest.permission.POST_NOTIFICATIONS,
                      )
                    }
                  },
                  isPhoneStatePermissionGranted = isPhoneStatePermissionGranted,
                  onRequestPhoneStatePermission = {
                    requestPhoneStatePermissionLauncher.launch(
                        Manifest.permission.READ_PHONE_STATE,
                    )
                  },
                  hasDualSim = hasDualSim(),
                  modifier = Modifier.padding(innerPadding),
              )
            }
          }
        }
      }
    }
  }

  private fun isSmsPermissionGranted(): Boolean =
      ContextCompat.checkSelfPermission(
          this,
          Manifest.permission.RECEIVE_SMS,
      ) == PackageManager.PERMISSION_GRANTED

  private fun isNotificationPermissionGranted(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
  }

  private fun isPhoneStatePermissionGranted(): Boolean =
      ContextCompat.checkSelfPermission(
          this,
          Manifest.permission.READ_PHONE_STATE,
      ) == PackageManager.PERMISSION_GRANTED

  private fun hasDualSim(): Boolean {
    return try {
      val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
      if (telephonyManager != null) {
        val phoneCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          telephonyManager.phoneCount
        } else {
          // For older devices, use reflection to get phone count
          try {
            val method = telephonyManager.javaClass.getMethod("getPhoneCount")
            method.invoke(telephonyManager) as? Int ?: 1
          } catch (e: Exception) {
            1
          }
        }
        phoneCount >= 2
      } else {
        false
      }
    } catch (e: Exception) {
      // If we can't determine, assume single SIM
      false
    }
  }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MailPreferencesScreen(
    context: Context,
    isSmsPermissionGranted: Boolean,
    onRequestPermission: () -> Unit = {},
    isNotificationPermissionGranted: Boolean,
    onRequestNotificationPermission: () -> Unit = {},
    isPhoneStatePermissionGranted: Boolean = false,
    onRequestPhoneStatePermission: () -> Unit = {},
    hasDualSim: Boolean = false,
    modifier: Modifier = Modifier,
) {

  val config by
      PreferencesManager.smtpConfigFlow(context)
          .collectAsState(initial = PreferencesManager.defaultConfig)
  val smtpHostState =
      rememberPreferenceTextState(config.smtpHost) {
        PreferencesManager.updateSmtpHost(context, it)
      }
  val smtpPortState =
      rememberPreferenceTextState(config.smtpPort.toString()) { value ->
        value.toIntOrNull()?.let { PreferencesManager.updateSmtpPort(context, it) }
      }
  val smtpUserState =
      rememberPreferenceTextState(config.smtpUser) {
        PreferencesManager.updateSmtpUser(context, it)
      }
  val smtpPasswordState =
      rememberPreferenceTextState(config.smtpPassword) {
        PreferencesManager.updateSmtpPassword(context, it)
      }
  val fromEmailState =
      rememberPreferenceTextState(config.fromEmail) {
        PreferencesManager.updateFromEmail(context, it)
      }
  val toEmailState =
      rememberPreferenceTextState(config.toEmail) { PreferencesManager.updateToEmail(context, it) }
  val sim1PhoneState =
      rememberPreferenceTextState(config.sim1PhoneNumber) {
        PreferencesManager.updateSim1PhoneNumber(context, it)
      }
  val sim2PhoneState =
      rememberPreferenceTextState(config.sim2PhoneNumber) {
        PreferencesManager.updateSim2PhoneNumber(context, it)
      }

  val coroutineScope = rememberCoroutineScope()

  val portNumber = smtpPortState.value.toIntOrNull()

  val encryptionExpandedState = rememberSaveable { mutableStateOf(false) }
  val encryptionLabel =
      when (config.encryptionMode) {
        SmtpEncryptionMode.SMTP_ENCRYPTION_MODE_NONE -> "None"
        SmtpEncryptionMode.SMTP_ENCRYPTION_MODE_SMTPS -> "SMTPS (SSL/TLS)"
        SmtpEncryptionMode.SMTP_ENCRYPTION_MODE_STARTTLS -> "STARTTLS"
        else -> "STARTTLS"
      }

  Box(modifier = modifier.fillMaxSize()) {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .background(Color.Transparent),
    ) {
      Card(
          modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
          colors =
              CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.surfaceVariant,
              ),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
          shape = MaterialTheme.shapes.medium,
      ) {
        Text(
            text =
                "SMS2Email automatically forwards received SMS to email via SMTP, even if the app is closed.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Card(
          modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
          colors =
              CardDefaults.cardColors(
                  containerColor =
                      if (isSmsPermissionGranted) {
                        MaterialTheme.colorScheme.primaryContainer
                      } else {
                        MaterialTheme.colorScheme.errorContainer
                      },
              ),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
          shape = MaterialTheme.shapes.medium,
      ) {
        Column(modifier = Modifier.padding(12.dp)) {
          Text(
              text =
                  if (isSmsPermissionGranted) "✓ SMS Permission: Granted"
                  else "✗ SMS Permission: Not Granted",
              style = MaterialTheme.typography.bodyLarge,
              color =
                  if (isSmsPermissionGranted) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                  } else {
                    MaterialTheme.colorScheme.onErrorContainer
                  },
          )

          if (!isSmsPermissionGranted) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onRequestPermission() },
                modifier = Modifier.fillMaxWidth(),
            ) {
              Text("Request SMS Permission")
            }
          }
        }
      }

      Card(
          modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
          colors =
              CardDefaults.cardColors(
                  containerColor =
                      if (isNotificationPermissionGranted) {
                        MaterialTheme.colorScheme.primaryContainer
                      } else {
                        MaterialTheme.colorScheme.errorContainer
                      },
              ),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
          shape = MaterialTheme.shapes.medium,
      ) {
        Column(modifier = Modifier.padding(12.dp)) {
          Text(
              text =
                  if (isNotificationPermissionGranted) "✓ Notification Permission: Granted"
                  else "✗ Notification Permission: Not Granted",
              style = MaterialTheme.typography.bodyLarge,
              color =
                  if (isNotificationPermissionGranted) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                  } else {
                    MaterialTheme.colorScheme.onErrorContainer
                  },
          )

          if (!isNotificationPermissionGranted) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onRequestNotificationPermission() },
                modifier = Modifier.fillMaxWidth(),
            ) {
              Text("Request Notification Permission")
            }
          }
        }
      }

      if (hasDualSim) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        if (isPhoneStatePermissionGranted) {
                          MaterialTheme.colorScheme.primaryContainer
                        } else {
                          MaterialTheme.colorScheme.errorContainer
                        },
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text =
                    if (isPhoneStatePermissionGranted) "✓ Phone State Permission: Granted"
                    else "✗ Phone State Permission: Not Granted",
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (isPhoneStatePermissionGranted) {
                      MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                      MaterialTheme.colorScheme.onErrorContainer
                    },
            )

            if (!isPhoneStatePermissionGranted) {
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                  text = "Required for dual SIM detection",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onErrorContainer,
              )
              Spacer(modifier = Modifier.height(8.dp))
              Button(
                  onClick = { onRequestPhoneStatePermission() },
                  modifier = Modifier.fillMaxWidth(),
              ) {
                Text("Request Phone State Permission")
              }
            }
          }
        }
      }

      Text(
          text = "SMTP Preferences",
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onBackground,
          modifier = Modifier.padding(bottom = 16.dp),
      )

      OutlinedTextField(
          value = smtpHostState.value,
          onValueChange = { smtpHostState.value = it },
          label = { Text("SMTP Host") },
          modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
          singleLine = true,
      )

      OutlinedTextField(
          value = smtpPortState.value,
          onValueChange = { smtpPortState.value = it },
          label = { Text("SMTP Port") },
          modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      )

      ExposedDropdownMenuBox(
          expanded = encryptionExpandedState.value,
          onExpandedChange = { encryptionExpandedState.value = !encryptionExpandedState.value },
          modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
      ) {
        OutlinedTextField(
            value = encryptionLabel,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Encryption") },
            trailingIcon = {
              ExposedDropdownMenuDefaults.TrailingIcon(expanded = encryptionExpandedState.value)
            },
            modifier =
                Modifier.menuAnchor(
                        type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                        enabled = true,
                    )
                    .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = encryptionExpandedState.value,
            onDismissRequest = { encryptionExpandedState.value = false },
        ) {
          DropdownMenuItem(
              text = { Text("STARTTLS") },
              onClick = {
                encryptionExpandedState.value = false
                coroutineScope.launch {
                  PreferencesManager.updateEncryptionMode(
                      context,
                      SmtpEncryptionMode.SMTP_ENCRYPTION_MODE_STARTTLS,
                  )
                }
              },
          )
          DropdownMenuItem(
              text = { Text("SMTPS (SSL/TLS)") },
              onClick = {
                encryptionExpandedState.value = false
                coroutineScope.launch {
                  PreferencesManager.updateEncryptionMode(
                      context,
                      SmtpEncryptionMode.SMTP_ENCRYPTION_MODE_SMTPS,
                  )
                }
              },
          )
          DropdownMenuItem(
              text = { Text("None") },
              onClick = {
                encryptionExpandedState.value = false
                coroutineScope.launch {
                  PreferencesManager.updateEncryptionMode(
                      context,
                      SmtpEncryptionMode.SMTP_ENCRYPTION_MODE_NONE,
                  )
                }
              },
          )
        }
      }

      val warningText =
          when {
            config.encryptionMode == SmtpEncryptionMode.SMTP_ENCRYPTION_MODE_NONE ->
                "Warning: No encryption selected. This may expose credentials and email content."
            portNumber == 465 &&
                config.encryptionMode != SmtpEncryptionMode.SMTP_ENCRYPTION_MODE_SMTPS ->
                "Warning: Port 465 is typically used with SMTPS (SSL/TLS)."
            portNumber != null &&
                portNumber != 465 &&
                config.encryptionMode == SmtpEncryptionMode.SMTP_ENCRYPTION_MODE_SMTPS ->
                "Warning: SMTPS (SSL/TLS) typically uses port 465."
            else -> null
          }
      if (warningText != null) {
        Text(
            text = warningText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 12.dp),
        )
      }

      OutlinedTextField(
          value = smtpUserState.value,
          onValueChange = { smtpUserState.value = it },
          label = { Text("SMTP Username") },
          modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
          singleLine = true,
      )

      OutlinedTextField(
          value = smtpPasswordState.value,
          onValueChange = { smtpPasswordState.value = it },
          label = { Text("SMTP Password") },
          modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
          singleLine = true,
          visualTransformation = PasswordVisualTransformation(),
      )

      if (config.smtpHost.contains("gmail", ignoreCase = true) &&
          config.smtpPassword.replace(" ", "").length != 16) {
        Text(
            text =
                "Your 16-digit Google \"App password\" needs to be entered, not your Google Account password.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Text(
            text = "Generate App Password",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            modifier =
                Modifier.padding(bottom = 12.dp).clickable {
                  val url = "https://myaccount.google.com/apppasswords"
                  val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                  context.startActivity(intent)
                },
        )
      }

      OutlinedTextField(
          value = fromEmailState.value,
          onValueChange = { fromEmailState.value = it },
          label = { Text("From Email Address") },
          modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
      )

      OutlinedTextField(
          value = toEmailState.value,
          onValueChange = { toEmailState.value = it },
          label = { Text("To Email Address") },
          modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
      )

      if (hasDualSim) {
        Text(
            text = "Dual SIM Settings",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
        )

        Text(
            text = "Configure phone numbers for each SIM card to identify which SIM received the SMS in the email subject.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        OutlinedTextField(
            value = sim1PhoneState.value,
            onValueChange = { sim1PhoneState.value = it },
            label = { Text("SIM 1 Phone Number") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )

        OutlinedTextField(
            value = sim2PhoneState.value,
            onValueChange = { sim2PhoneState.value = it },
            label = { Text("SIM 2 Phone Number") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      Button(
          onClick = {
            Toast.makeText(context, "Sending email ...", Toast.LENGTH_SHORT).show()
            MailSender().send(context, "[TEST]", "This is a test email.", -1)
          },
          modifier = Modifier.fillMaxWidth(),
      ) {
        Text("Send Test Email")
      }

      Row(
          modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
          horizontalArrangement = Arrangement.End,
      ) {
        Text(
            text = "Licenses...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier.clickable {
                  context.startActivity(Intent(context, AboutActivity::class.java))
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
    if (storedValue != inputState.value) {
      inputState.value = storedValue
    }
  }

  LaunchedEffect(inputState.value, storedValue) {
    val currentValue = inputState.value
    if (currentValue == storedValue) return@LaunchedEffect
    delay(debounceMillis)
    if (currentValue == inputState.value && currentValue != storedValue) {
      onPersist(currentValue)
    }
  }

  return inputState
}

@Preview(showBackground = true)
@Composable
fun MailPreferencesScreenPreview() {
  SMS2EmailTheme {
    // Note: Preview doesn't have access to actual SharedPreferences
    // In a real preview, you'd need to mock the context
  }
}
