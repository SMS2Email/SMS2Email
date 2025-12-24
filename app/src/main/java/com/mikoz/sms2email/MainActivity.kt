package com.mikoz.sms2email

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.mikoz.sms2email.ui.theme.SMS2EmailTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "SMS permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "SMS permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkAndRequestSmsPermission()

        setContent {
            SMS2EmailTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MailPreferencesScreen(
                        context = this,
                        onRequestPermission = { requestPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS) },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun checkAndRequestSmsPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECEIVE_SMS
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
            }
            else -> {
                // Request permission
                requestPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
            }
        }
    }
}

@Composable
fun MailPreferencesScreen(
    context: Context,
    onRequestPermission: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isSmsPermissionGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECEIVE_SMS
    ) == PackageManager.PERMISSION_GRANTED

    val config by PreferencesManager.smtpConfigFlow(context)
        .collectAsState(initial = PreferencesManager.defaultConfig)
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = "SMS2Email automatically forwards received SMS to email via SMTP, even if the app is closed.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = if (isSmsPermissionGranted) "✓ SMS Permission: Granted" else "✗ SMS Permission: Not Granted",
            color = if (isSmsPermissionGranted) Color.Green else Color.Red,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = "SMTP Preferences",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (!isSmsPermissionGranted) {
            Button(
                onClick = { onRequestPermission() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text("Request SMS Permission")
            }
        }

        OutlinedTextField(
            value = config.smtpHost,
            onValueChange = { value ->
                coroutineScope.launch { PreferencesManager.updateSmtpHost(context, value) }
            },
            label = { Text("SMTP Host") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = config.smtpPort.toString(),
            onValueChange = { value ->
                value.toIntOrNull()?.let { port ->
                    coroutineScope.launch { PreferencesManager.updateSmtpPort(context, port) }
                }
            },
            label = { Text("SMTP Port") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        OutlinedTextField(
            value = config.smtpUser,
            onValueChange = { value ->
                coroutineScope.launch { PreferencesManager.updateSmtpUser(context, value) }
            },
            label = { Text("SMTP Username") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = config.smtpPassword,
            onValueChange = { value ->
                coroutineScope.launch { PreferencesManager.updateSmtpPassword(context, value) }
            },
            label = { Text("SMTP Password") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )

        if (config.smtpHost.contains("gmail", ignoreCase = true) &&
            config.smtpPassword.replace(" ", "").length != 16
        ) {
            Text(
                text = "Your 16-digit Google \"App password\" needs to be entered, not your Google Account password.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Red,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Generate App Password",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .clickable {
                        val url = "https://myaccount.google.com/apppasswords"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    }
            )
        }

        OutlinedTextField(
            value = config.fromEmail,
            onValueChange = { value ->
                coroutineScope.launch { PreferencesManager.updateFromEmail(context, value) }
            },
            label = { Text("From Email Address") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )

        OutlinedTextField(
            value = config.toEmail,
            onValueChange = { value ->
                coroutineScope.launch { PreferencesManager.updateToEmail(context, value) }
            },
            label = { Text("To Email Address") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )

        Button(
            onClick = {
                Toast.makeText(context, "Sending email ...", Toast.LENGTH_SHORT).show()
                MailSender().send(context, "test", "test")
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Send Test Email")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MailPreferencesScreenPreview() {
    SMS2EmailTheme {
        // Note: Preview doesn't have access to actual SharedPreferences
        // In a real preview, you'd need to mock the context
    }
}
