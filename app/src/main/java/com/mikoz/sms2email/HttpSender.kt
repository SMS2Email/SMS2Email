package io.github.sms2email.sms2email

import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class HttpSender : MessageSender {
  override fun send(
      context: Context,
      sender: String,
      content: String,
  ) {
    Thread { sendSync(context, sender, content) }.start()
  }

  private fun sendSync(
      context: Context,
      sender: String,
      content: String,
  ) {
    val config = PreferencesManager.getConfigBlocking(context)
    try {
      val endpointUrl = config.httpUrl.trim()
      if (endpointUrl.isEmpty()) {
        throw IllegalStateException("HTTP endpoint URL is not configured")
      }

      val payload = buildJsonPayload(sender, content, System.currentTimeMillis())
      postJson(endpointUrl, payload, config.httpAuthHeader)

      NotificationHelper.createNotificationChannel(context)

      val notificationManager =
          context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

      val builder =
          NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
              .setContentTitle("Transferred SMS from $sender")
              .setContentText(content)
              .setSmallIcon(android.R.drawable.ic_dialog_email)

      notificationManager.notify(2, builder.build())
    } catch (e: Exception) {
      val sw = StringWriter()
      val pw = PrintWriter(sw)
      e.printStackTrace(pw)
      val stackTrace = sw.toString()

      Handler(Looper.getMainLooper()).post {
        Toast.makeText(
                context,
                "Failed to send HTTP request\n${e.message}",
                Toast.LENGTH_LONG,
            )
            .show()
      }

      NotificationHelper.createNotificationChannel(context)

      val notificationManager =
          context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

      val builder =
          NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
              .setContentTitle("Failed to send HTTP request")
              .setContentText(e.message)
              .setStyle(
                  NotificationCompat.BigTextStyle().bigText("${e.message}\n\n$stackTrace"),
              )
              .setSmallIcon(android.R.drawable.ic_dialog_email)

      notificationManager.notify(1, builder.build())
    }
  }

  internal fun postJson(
      endpointUrl: String,
      jsonPayload: String,
      authHeader: String,
  ) {
    val connection = (URL(endpointUrl).openConnection() as HttpURLConnection)
    try {
      connection.requestMethod = "POST"
      connection.connectTimeout = 15_000
      connection.readTimeout = 15_000
      connection.doOutput = true
      connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
      connection.setRequestProperty("Accept", "application/json")

      parseAuthHeader(authHeader)?.let { (headerName, headerValue) ->
        connection.setRequestProperty(headerName, headerValue)
      }

      val bodyBytes = jsonPayload.toByteArray(StandardCharsets.UTF_8)
      connection.setFixedLengthStreamingMode(bodyBytes.size)
      connection.outputStream.use { output -> output.write(bodyBytes) }

      val responseCode = connection.responseCode
      if (responseCode !in 200..299) {
        val errorBody =
            try {
              connection.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }
            } catch (_: Exception) {
              null
            }
        val detail = if (!errorBody.isNullOrBlank()) " - $errorBody" else ""
        throw IOException("HTTP $responseCode ${connection.responseMessage.orEmpty()}$detail".trim())
      }
    } finally {
      connection.disconnect()
    }
  }

  companion object {
    @JvmStatic
    fun buildJsonPayload(
        sender: String,
        message: String,
        timestamp: Long,
    ): String =
        "{\"sender\":\"${escapeJson(sender)}\",\"message\":\"${escapeJson(message)}\",\"timestamp\":$timestamp}"

    private fun escapeJson(value: String): String = buildString(value.length) {
      for (ch in value) {
        when (ch) {
          '\\' -> append("\\\\")
          '"' -> append("\\\"")
          '\b' -> append("\\b")
          '\u000C' -> append("\\f")
          '\n' -> append("\\n")
          '\r' -> append("\\r")
          '\t' -> append("\\t")
          else ->
              if (ch < ' ') {
                append(String.format("\\u%04x", ch.code))
              } else {
                append(ch)
              }
        }
      }
    }

    @JvmStatic
    fun parseAuthHeader(rawHeader: String): Pair<String, String>? {
      val trimmed = rawHeader.trim()
      if (trimmed.isEmpty()) return null
      val colonIndex = trimmed.indexOf(':')
      return if (colonIndex > 0) {
        val name = trimmed.substring(0, colonIndex).trim()
        val value = trimmed.substring(colonIndex + 1).trim()
        if (name.isNotEmpty() && value.isNotEmpty()) {
          name to value
        } else {
          "Authorization" to trimmed
        }
      } else {
        "Authorization" to trimmed
      }
    }
  }
}
