package com.mikoz.sms2email

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
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
import com.mikoz.sms2email.ui.theme.SMS2EmailTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
  private val smsPermissionGrantedFlow = MutableStateFlow(false)
  private val requestPermissionLauncher =
      registerForActivityResult(
          ActivityResultContracts.RequestPermission(),
      ) { isGranted: Boolean ->
        if (!isGranted) {
          Toast.makeText(this, "SMS permission denied", Toast.LENGTH_SHORT).show()
        }
        smsPermissionGrantedFlow.value = isGranted
      }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // window.setBackgroundDrawableResource(R.drawable.background)
    enableEdgeToEdge()

    smsPermissionGrantedFlow.value = isSmsPermissionGranted()
    val initialSmsPermissionGranted = smsPermissionGrantedFlow.value

    setContent {
      SMS2EmailTheme {
        val isDark = isSystemInDarkTheme()
        val isSmsPermissionGranted by
            smsPermissionGrantedFlow.collectAsState(initial = initialSmsPermissionGranted)
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
}

@Composable
fun MailPreferencesScreen(
    context: Context,
    isSmsPermissionGranted: Boolean,
    onRequestPermission: () -> Unit = {},
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

      Text(
          text = "SMTP Preferences",
          style = MaterialTheme.typography.titleLarge,
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

      Button(
          onClick = {
            Toast.makeText(context, "Sending email ...", Toast.LENGTH_SHORT).show()
            MailSender().send(context, "test", "test")
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
