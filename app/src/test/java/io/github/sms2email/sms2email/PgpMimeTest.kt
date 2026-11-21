package io.github.sms2email.sms2email

import io.github.sms2email.sms2email.preferences.SimInput
import io.github.sms2email.sms2email.send.ENCRYPTED_SUBJECT
import io.github.sms2email.sms2email.send.SmsEvent
import io.github.sms2email.sms2email.send.pgpMimeMultipart
import io.github.sms2email.sms2email.send.secretMimeEntity
import io.github.sms2email.sms2email.send.secretText
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Properties
import javax.mail.Session
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.internet.MimeUtility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PgpMimeTest {
  private val event =
      SmsEvent.create("+49 170 1234567", "Your code is 482913. Grüße", slotIndex = 0)
  private val inputs = listOf(SimInput("sim-1", slotIndex = 0, subscriptionId = 41, label = "Work"))

  @Test
  fun encryptedTextCarriesSenderTimeSimAndSms() {
    val text = secretText(event, inputs)

    assertTrue(text.startsWith("From: +49 170 1234567\nReceived: "))
    assertTrue(text.contains("\nSIM: Work\n\n"))
    assertTrue(text.endsWith("Your code is 482913. Grüße"))
  }

  @Test
  fun simFallsBackToSlotNumberWithoutALabel() {
    assertTrue(secretText(event, emptyList()).contains("\nSIM: SIM 1\n"))
    assertFalse(secretText(SmsEvent.create("+1", "x"), emptyList()).contains("SIM:"))
  }

  @Test
  fun secretEntityIsTextWithProtectedSubject() {
    val part = MimeMessage(null, ByteArrayInputStream(secretMimeEntity(event, inputs)))

    assertTrue(part.contentType.contains("protected-headers=\"v1\""))
    assertEquals("SMS from +49 170 1234567", MimeUtility.decodeText(part.getHeader("Subject")[0]))
    // MIME text uses CRLF line endings.
    assertEquals(secretText(event, inputs), (part.content as String).replace("\r\n", "\n"))
  }

  @Test
  fun emailIsPgpMimeWithOnlyCiphertextOutside() {
    val armored = "-----BEGIN PGP MESSAGE-----\n\nhQEMA...\n-----END PGP MESSAGE-----\n"
    val message = MimeMessage(Session.getInstance(Properties()))
    message.subject = ENCRYPTED_SUBJECT
    message.setContent(pgpMimeMultipart(armored))
    message.saveChanges()
    val raw = ByteArrayOutputStream().also { message.writeTo(it) }.toString("UTF-8")

    assertTrue(message.contentType.startsWith("multipart/encrypted"))
    assertTrue(message.contentType.contains("protocol=\"application/pgp-encrypted\""))
    val parts = message.content as MimeMultipart
    assertEquals(2, parts.count)
    assertTrue(parts.getBodyPart(0).isMimeType("application/pgp-encrypted"))
    assertEquals("Version: 1", (parts.getBodyPart(0).inputStream.readBytes()).decodeToString().trim())
    assertTrue(parts.getBodyPart(1).isMimeType("application/octet-stream"))
    assertTrue(raw.contains(armored.trim().replace("\n", "\r\n")))
    assertFalse("SMTP needs CRLF; bare LF found", Regex("(?<!\r)\n").containsMatchIn(raw))
    assertFalse(raw.contains("1234567"))
  }
}
