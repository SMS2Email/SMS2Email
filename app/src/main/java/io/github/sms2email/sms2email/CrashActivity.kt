package io.github.sms2email.sms2email

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.sms2email.sms2email.ui.theme.SMS2EmailTheme

class CrashActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val crashMessage = intent.getStringExtra("crash_message") ?: "Unknown error"
    val stackTrace = intent.getStringExtra("stack_trace") ?: ""

    setContent {
      SMS2EmailTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          CrashScreen(
              context = this,
              crashMessage = crashMessage,
              stackTrace = stackTrace,
              modifier = Modifier.padding(innerPadding),
          )
        }
      }
    }
  }
}

@Composable
fun CrashScreen(
    context: Context,
    crashMessage: String,
    stackTrace: String,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
  ) {
    Text(
        text = "App Crashed",
        color = Color.Red,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(bottom = 16.dp),
    )

    Text(
        text = crashMessage,
        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier.fillMaxWidth().padding(8.dp, 4.dp).padding(bottom = 16.dp),
        color = Color.Gray,
    )

    Text(
        text = stackTrace,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier.fillMaxWidth().padding(8.dp, 4.dp).padding(bottom = 24.dp),
        color = Color.Gray,
    )

    Button(
        onClick = {
          val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
          val clip = ClipData.newPlainText("crash_info", "$crashMessage\n\n$stackTrace")
          clipboard.setPrimaryClip(clip)
          Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        },
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
      Text("Copy to Clipboard")
    }

    Button(
        onClick = {
          // Restart the app
          val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
          if (intent != null) {
            context.startActivity(intent)
          }
          (context as? ComponentActivity)?.finish()
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
      Text("Restart App")
    }
  }
}
