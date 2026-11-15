package io.github.sms2email.sms2email

import io.github.sms2email.sms2email.preferences.EmailPreference
import io.github.sms2email.sms2email.preferences.MatrixPreference
import io.github.sms2email.sms2email.preferences.SimInput
import io.github.sms2email.sms2email.preferences.legacySmtpEmail
import io.github.sms2email.sms2email.send.SmsEvent
import io.github.sms2email.sms2email.send.acceptsInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForwardingConfigurationTest {
  @Test
  fun eachOutputCanSelectItsOwnInputs() {
    val emailInputs =
        listOf(
            SimInput("sim-1", slotIndex = 0, subscriptionId = 41, label = "SIM 1"),
            SimInput(
                "sim-2",
                slotIndex = 1,
                subscriptionId = 42,
                label = "SIM 2",
                enabled = false,
            ),
        )
    val matrixInputs =
        listOf(SimInput("sim-2", slotIndex = 1, subscriptionId = 42, label = "SIM 2"))

    assertTrue(acceptsInput(emailInputs, SmsEvent.create("+1", "one", slotIndex = 0)))
    assertFalse(acceptsInput(emailInputs, SmsEvent.create("+1", "two", slotIndex = 1)))
    assertTrue(acceptsInput(matrixInputs, SmsEvent.create("+1", "two", subscriptionId = 42)))
    assertFalse(acceptsInput(matrixInputs, SmsEvent.create("+1", "one", subscriptionId = 41)))
    assertFalse(acceptsInput(emailInputs, SmsEvent.create("+1", "unknown")))
  }

  @Test
  fun unconfiguredOutputAcceptsSms() {
    assertTrue(acceptsInput(emptyList(), SmsEvent.create("+1", "message")))
  }

  @Test
  fun legacySmtpPreferencesMigrateToEmailOutput() {
    val email =
        legacySmtpEmail(
            LegacySmtpPreferences.newBuilder()
                .setSmtpHost("smtp.example.com")
                .setSmtpPort(587)
                .setSmtpUser("user@example.com")
                .setSmtpPassword("app-password")
                .setFromEmail("from@example.com")
                .setToEmail("to@example.com")
                .setEncryptionMode(LegacySmtpEncryptionMode.LEGACY_SMTP_ENCRYPTION_MODE_STARTTLS)
                .build())

    assertEquals("legacy-smtp", email.id)
    assertTrue(email.enabled)
    assertEquals("smtp.example.com", email.smtpHost)
    assertEquals(587, email.smtpPort)
    assertEquals("user@example.com", email.smtpUser)
    assertEquals("app-password", email.smtpPassword)
    assertEquals("from@example.com", email.fromEmail)
    assertEquals("to@example.com", email.emailTo)
    assertEquals(
        io.github.sms2email.sms2email.SmtpEncryptionMode.SMTP_ENCRYPTION_MODE_STARTTLS,
        email.encryptionMode,
    )
  }

  @Test
  fun sendersHaveSeparatePreferenceTypes() {
    assertTrue(EmailPreference("email", true).isConfigured().not())
    assertTrue(MatrixPreference("matrix", true).isConfigured().not())
  }
}
