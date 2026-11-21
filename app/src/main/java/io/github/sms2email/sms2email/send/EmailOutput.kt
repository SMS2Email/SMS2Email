package io.github.sms2email.sms2email.send

import android.content.Context
import io.github.sms2email.sms2email.PgpMode
import io.github.sms2email.sms2email.SmtpEncryptionMode
import io.github.sms2email.sms2email.preferences.EmailPreference
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun sendEmail(context: Context, event: SmsEvent, email: EmailPreference) =
    withContext(Dispatchers.IO) {
      val properties =
          Properties().apply {
            setProperty("mail.smtp.host", email.smtpHost)
            setProperty("mail.smtp.port", email.smtpPort.toString())
            setProperty("mail.smtp.auth", "true")
            setProperty("mail.smtp.connectiontimeout", "15000")
            setProperty("mail.smtp.timeout", "15000")
            setProperty("mail.smtp.writetimeout", "15000")
          }

      when (email.encryptionMode) {
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

      val session =
          Session.getInstance(
              properties,
              object : Authenticator() {
                override fun getPasswordAuthentication() =
                    PasswordAuthentication(email.smtpUser, email.smtpPassword)
              },
          )
      val message: Message = MimeMessage(session)
      message.setFrom(InternetAddress(email.fromEmail))
      message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(email.emailTo))
      if (email.pgpMode == PgpMode.PGP_MODE_OFF) {
        message.subject = "SMS from ${event.sender}"
        message.setText(event.body)
        message.setHeader("X-SMS2Email-Event-Id", event.id)
      } else {
        // Any failure here throws, so the SMS is never sent unsigned or unencrypted by mistake.
        // The event id header is left out because it is a hash of the sender and the SMS text.
        message.subject = ENCRYPTED_SUBJECT
        message.setContent(pgpMimeMultipart(encryptForEmail(context, event, email)))
      }
      Transport.send(message)
    }

private suspend fun encryptForEmail(
    context: Context,
    event: SmsEvent,
    email: EmailPreference,
): String =
    signAndEncryptInBackground(
        context,
        secretMimeEntity(event, email.inputs),
        email.pgpKeyIds,
        email.pgpSignKeyId,
    )
