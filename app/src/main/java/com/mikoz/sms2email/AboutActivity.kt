package io.github.sms2email.sms2email

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Modifier
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import io.github.sms2email.sms2email.ui.theme.SMS2EmailTheme

class AboutActivity : ComponentActivity() {
  @OptIn(ExperimentalMaterial3Api::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    setContent {
      SMS2EmailTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
              TopAppBar(
                  title = { Text("Licenses") },
                  navigationIcon = {
                    IconButton(onClick = { finish() }) {
                      Icon(
                          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                          contentDescription = "Back",
                      )
                    }
                  },
              )
            },
        ) { innerPadding ->
          LibrariesContainer(modifier = Modifier.padding(innerPadding).fillMaxSize())
        }
      }
    }
  }
}
