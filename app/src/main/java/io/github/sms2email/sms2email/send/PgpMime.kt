package io.github.sms2email.sms2email.send

import io.github.sms2email.sms2email.preferences.SimInput
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.activation.DataHandler
import javax.mail.internet.ContentType
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMultipart
import javax.mail.internet.MimeUtility
import javax.mail.util.ByteArrayDataSource

/** Outer subject of every encrypted email; the real details are only inside the ciphertext. */
const val ENCRYPTED_SUBJECT = "SMS"

/**
 * The text that gets encrypted: who sent the SMS, when, on which SIM, then the SMS itself.
 */
fun secretText(event: SmsEvent, inputs: List<SimInput>): String = buildString {
  append("From: ").append(event.sender).append('\n')
  append("Received: ").append(formatTime(event.receivedAt)).append('\n')
  simLabel(event, inputs)?.let { append("SIM: ").append(it).append('\n') }
  append('\n')
  append(event.body)
}

/**
 * The MIME entity that gets encrypted (RFC 3156 section 4). It carries the real subject as a
 * protected header, which Thunderbird shows after decrypting.
 */
fun secretMimeEntity(event: SmsEvent, inputs: List<SimInput>): ByteArray {
  val part = UpdatableBodyPart()
  part.setText(secretText(event, inputs), "UTF-8")
  part.updateHeaders()
  part.setHeader("Content-Type", "text/plain; charset=UTF-8; protected-headers=\"v1\"")
  part.setHeader("Subject", MimeUtility.encodeText("SMS from ${event.sender}", "UTF-8", null))
  return ByteArrayOutputStream().also { part.writeTo(it) }.toByteArray()
}

/**
 * Wraps an ASCII-armored OpenPGP message as PGP/MIME (RFC 3156 `multipart/encrypted`), the format
 * Thunderbird and K-9/Thunderbird for Android with OpenKeychain decrypt automatically.
 */
fun pgpMimeMultipart(armoredMessage: String): MimeMultipart {
  val multipart = PgpEncryptedMultipart()

  val version = MimeBodyPart()
  version.dataHandler =
      DataHandler(ByteArrayDataSource("Version: 1\r\n".toByteArray(), PGP_ENCRYPTED_TYPE))
  version.setHeader("Content-Description", "PGP/MIME version identification")
  multipart.addBodyPart(version)

  // application/octet-stream is not line-canonicalized by JavaMail, and SMTP requires CRLF;
  // some servers reject bare LF.
  val crlfArmor = armoredMessage.replace("\r\n", "\n").replace("\n", "\r\n")
  val encrypted = MimeBodyPart()
  encrypted.dataHandler =
      DataHandler(
          ByteArrayDataSource(
              crlfArmor.toByteArray(Charsets.US_ASCII),
              "application/octet-stream; name=\"encrypted.asc\""))
  encrypted.setHeader("Content-Description", "OpenPGP encrypted message")
  encrypted.disposition = "inline"
  encrypted.fileName = "encrypted.asc"
  multipart.addBodyPart(encrypted)

  return multipart
}

private const val PGP_ENCRYPTED_TYPE = "application/pgp-encrypted"

/** `multipart/encrypted` needs a `protocol` parameter, which [MimeMultipart] cannot set itself. */
private class PgpEncryptedMultipart : MimeMultipart("encrypted") {
  init {
    val type = ContentType(contentType)
    type.setParameter("protocol", PGP_ENCRYPTED_TYPE)
    contentType = type.toString()
  }
}

/** Exposes [MimeBodyPart.updateHeaders] so a standalone part gets its MIME headers. */
private class UpdatableBodyPart : MimeBodyPart() {
  public override fun updateHeaders() = super.updateHeaders()
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date(millis))

private fun simLabel(event: SmsEvent, inputs: List<SimInput>): String? {
  val input =
      inputs.firstOrNull { input ->
        when {
          event.slotIndex != null -> input.slotIndex == event.slotIndex
          event.subscriptionId != null -> input.subscriptionId == event.subscriptionId
          else -> false
        }
      }
  return input?.label ?: event.slotIndex?.let { "SIM ${it + 1}" }
}
