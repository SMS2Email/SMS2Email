package io.github.sms2email.sms2email

import io.github.sms2email.sms2email.EmailPreference as EmailProto
import io.github.sms2email.sms2email.MatrixPreference as MatrixProto
import io.github.sms2email.sms2email.preferences.EmailPreference
import io.github.sms2email.sms2email.preferences.LegacyPreferencesMigration
import io.github.sms2email.sms2email.preferences.MatrixPreference
import io.github.sms2email.sms2email.preferences.PreferencesSerializer
import io.github.sms2email.sms2email.preferences.ResetMatrixPreferencesMigration
import io.github.sms2email.sms2email.preferences.SimInput
import io.github.sms2email.sms2email.preferences.legacySmtpEmail
import io.github.sms2email.sms2email.send.SmsEvent
import io.github.sms2email.sms2email.send.acceptsInput
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForwardingConfigurationTest {
  @Test
  fun freshInstallationStartsWithOneEmailAndOneMatrixOutput() {
    val defaults = PreferencesSerializer.defaultValue

    assertEquals(1, defaults.emailsCount)
    assertEquals(1, defaults.matricesCount)
    assertEquals("default-smtp", defaults.emailsList.single().id)
    assertEquals("default-matrix", defaults.matricesList.single().id)
  }

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
  fun previousPreferencesMigrateSharedInputsToEveryOutput() = runBlocking {
    val oldPreferences =
        LegacyPreferences.newBuilder()
            .setInputsSelected(true)
            .addInputs(
                LegacyInputPreference.newBuilder()
                    .setId("sim-1")
                    .setSlotIndex(0)
                    .setSubscriptionId(41)
                    .setLabel("SIM 1")
                    .setEnabled(false)
                    .build())
            .addEmails(
                LegacyEmailPreference.newBuilder()
                    .setId("email")
                    .setEnabled(true)
                    .setSmtpHost("smtp.example.com")
                    .setSmtpPort(587)
                    .setSmtpUser("user@example.com")
                    .setSmtpToken("legacy-token")
                    .setFromEmail("from@example.com")
                    .setEmailTo("to@example.com")
                    .build())
            .addMatrices(
                LegacyMatrixPreference.newBuilder()
                    .setId("matrix")
                    .setEnabled(true)
                    .setHomeserver("https://matrix.example.com")
                    .build())
            .build()
    val currentPreferences = Preferences.parseFrom(oldPreferences.toByteArray())
    val migrated = LegacyPreferencesMigration.migrate(currentPreferences)

    assertEquals("legacy-token", migrated.emailsList.single().smtpPassword)
    assertEquals(1, migrated.emailsList.single().inputsCount)
    assertEquals(1, migrated.matricesList.single().inputsCount)
    assertFalse(migrated.emailsList.single().inputsList.single().enabled)
  }

  @Test
  fun rustSdkMigrationDropsOldMatrixSettingsAndToken() = runBlocking {
    val oldPreferences =
        Preferences.newBuilder()
            .addEmails(EmailProto.newBuilder().setId("email").build())
            .addMatrices(
                MatrixProto.newBuilder().setId("legacy-matrix").setAccessToken("old-token").build())
            .build()

    val migrated = ResetMatrixPreferencesMigration.migrate(oldPreferences)

    assertTrue(migrated.matrixRustMigrationComplete)
    assertEquals(1, migrated.emailsCount)
    assertEquals("default-matrix", migrated.matricesList.single().id)
    assertEquals("", migrated.matricesList.single().accessToken)
  }

  @Test
  fun sendersHaveSeparatePreferenceTypes() {
    assertTrue(EmailPreference("email", true).isConfigured().not())
    assertTrue(MatrixPreference("matrix", true).isConfigured().not())
  }
}
