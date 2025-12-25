package com.mikoz.sms2email.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Simplified theme with basic light/dark switching using Material3 defaults.
@Composable
fun SMS2EmailTheme(content: @Composable () -> Unit) {
  val darkTheme = isSystemInDarkTheme()
  val colorScheme =
      when {
        !darkTheme && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
          val context = LocalContext.current
          if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme ->
            darkColorScheme(
                primary = Color(0xEEFFEDA6),
                secondary = Color(0xFF976e4f),
                tertiary = Color(0xFFFFFF6D),
                background = Color(0xFF00000a),
            )
        else -> lightColorScheme()
      }
  MaterialTheme(
      colorScheme = colorScheme,
      content = content,
  )
}
