package io.github.sms2email.sms2email.preferences

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.dataStoreFile
import io.github.sms2email.sms2email.EmailPreference as EmailProto
import io.github.sms2email.sms2email.LegacySmtpPreferences
import io.github.sms2email.sms2email.Preferences
import io.github.sms2email.sms2email.SmtpEncryptionMode

internal class LegacySmtpPreferencesMigration(
    private val context: Context,
) : DataMigration<Preferences> {
  private val legacyFile
    get() = context.dataStoreFile("smtp_prefs.pb")

  override suspend fun shouldMigrate(currentData: Preferences): Boolean =
      legacyFile.exists() && currentData.emailsCount == 0 && currentData.matricesCount == 0

  override suspend fun migrate(currentData: Preferences): Preferences {
    val legacy =
        runCatching { legacyFile.inputStream().use { LegacySmtpPreferences.parseFrom(it) } }
            .getOrNull() ?: return currentData

    return currentData.toBuilder().addEmails(legacySmtpEmail(legacy)).build()
  }

  override suspend fun cleanUp() {
    legacyFile.delete()
  }
}

internal fun legacySmtpEmail(legacy: LegacySmtpPreferences): EmailProto =
    EmailProto.newBuilder()
        .setId("legacy-smtp")
        .setEnabled(true)
        .setEmailTo(legacy.toEmail)
        .setSmtpHost(legacy.smtpHost)
        .setSmtpPort(legacy.smtpPort)
        .setSmtpUser(legacy.smtpUser)
        .setSmtpPassword(legacy.smtpPassword)
        .setFromEmail(legacy.fromEmail)
        .setEncryptionMode(
            when (legacy.encryptionModeValue) {
              1 -> SmtpEncryptionMode.SMTP_ENCRYPTION_MODE_SMTPS
              2 -> SmtpEncryptionMode.SMTP_ENCRYPTION_MODE_NONE
              else -> SmtpEncryptionMode.SMTP_ENCRYPTION_MODE_STARTTLS
            })
        .build()
