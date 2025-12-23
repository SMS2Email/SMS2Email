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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.mikoz.sms2email.ui.theme.SMS2EmailTheme

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
fun MailPreferencesScreen(context: Context, modifier: Modifier = Modifier) {
    val sharedPreferences = context.getSharedPreferences("", Context.MODE_PRIVATE)

    fun savePreference(key: String, value: String) {
        sharedPreferences.edit().putString(key, value).apply()
    }

    fun savePreference(key: String, value: Int) {
        sharedPreferences.edit().putInt(key, value).apply()
    }

    val isSmsPermissionGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECEIVE_SMS
    ) == PackageManager.PERMISSION_GRANTED

    var smtpHost by remember {
        mutableStateOf(sharedPreferences.getString("smtp.host", "smtp.gmail.com") ?: "smtp.gmail.com")
    }
    var smtpPort by remember {
        mutableStateOf(sharedPreferences.getInt("smtp.port", 587).toString())
    }
    var smtpUser by remember {
        mutableStateOf(sharedPreferences.getString("smtp.user", "") ?: "")
    }
    var smtpPassword by remember {
        mutableStateOf(sharedPreferences.getString("smtp.password", "") ?: "")
    }
    var fromEmail by remember {
        mutableStateOf(sharedPreferences.getString("from", "") ?: "")
    }
    var toEmail by remember {
        mutableStateOf(sharedPreferences.getString("to", "") ?: "")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Mail Preferences",
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = if (isSmsPermissionGranted) "✓ SMS Permission: Granted" else "✗ SMS Permission: Not Granted",
            color = if (isSmsPermissionGranted) Color.Green else Color.Red,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = smtpHost,
            onValueChange = {
                smtpHost = it
                savePreference("smtp.host", it)
            },
            label = { Text("SMTP Host") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = smtpPort,
            onValueChange = {
                smtpPort = it
                it.toIntOrNull()?.let { port ->
                    savePreference("smtp.port", port)
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
            value = smtpUser,
            onValueChange = {
                smtpUser = it
                savePreference("smtp.user", it)
            },
            label = { Text("SMTP Username") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = smtpPassword,
            onValueChange = {
                smtpPassword = it
                savePreference("smtp.password", it)
            },
            label = { Text("SMTP Password") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )

        OutlinedTextField(
            value = fromEmail,
            onValueChange = {
                fromEmail = it
                savePreference("from", it)
            },
            label = { Text("From Email Address") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )

        OutlinedTextField(
            value = toEmail,
            onValueChange = {
                toEmail = it
                savePreference("to", it)
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
