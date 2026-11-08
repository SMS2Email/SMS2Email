package io.github.sms2email.sms2email

import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

fun sendMail(context: Context, subject: String, content: String) {
  Thread { sendMailSync(context, subject, content) }.start()
}

private fun sendMailSync(context: Context, subject: String, content: String) {
  val config = PreferencesManager.getConfigBlocking(context)
  val smtpPort = config.smtpPort
  val properties = Properties()
  properties["mail.smtp.host"] = config.smtpHost
  properties["mail.smtp.port"] = smtpPort
  properties["mail.smtp.auth"] = "true"

  // Configure transport encryption.
  when (config.encryptionMode) {
    SmtpEncryptionMode.SMTP_ENCRYPTION_MODE_NONE -> {
      properties.setProperty("mail.smtp.starttls.enable", "false")
      properties.setProperty("mail.smtp.ssl.enable", "false")
    }
    SmtpEncryptionMode.SMTP_ENCRYPTION_MODE_SMTPS -> {
      properties.setProperty("mail.smtp.starttls.enable", "false")
      properties.setProperty("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
      properties.setProperty("mail.smtp.socketFactory.fallback", "false")
      properties.setProperty("mail.smtp.ssl.checkserveridentity", "true")
    }
    else -> {
      properties.setProperty("mail.smtp.starttls.enable", "true")
      properties.setProperty("mail.smtp.starttls.required", "true")
      properties.setProperty("mail.smtp.ssl.checkserveridentity", "true")
    }
  }

  try {
    val session =
        Session.getInstance(
            properties,
            object : Authenticator() {
              override fun getPasswordAuthentication() =
                  PasswordAuthentication(config.smtpUser, config.smtpPassword)
            })
    val message: Message = MimeMessage(session)
    message.setFrom(InternetAddress.parse("$subject <${config.fromEmail}>")[0])
    message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(config.toEmail))
    message.subject = "SMS from $subject"
    message.setText(content)
    Transport.send(message)

    createNotificationChannel(context)
    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val notification =
        NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Transferred SMS from $subject")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .build()

    notificationManager.notify(2, notification)
  } catch (exception: Exception) {
    val stackTrace =
        StringWriter().also { writer -> exception.printStackTrace(PrintWriter(writer)) }.toString()

    Handler(Looper.getMainLooper()).post {
      Toast.makeText(context, "Failed to send email\n${exception.message}", Toast.LENGTH_LONG)
          .show()
    }

    createNotificationChannel(context)
    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val notification =
        NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Failed to send email")
            .setContentText(exception.message)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText("${exception.message}\n\n$stackTrace"))
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .build()

    notificationManager.notify(1, notification)
  }
}
