package io.github.sms2email.sms2email.preferences

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.dataStoreFile
import io.github.sms2email.sms2email.EmailPreference as EmailProto
import io.github.sms2email.sms2email.LegacySmtpPreferences
import io.github.sms2email.sms2email.Preferences
import io.github.sms2email.sms2email.SmtpEncryptionMode
import java.io.File

internal class LegacySmtpPreferencesMigration(
    private val context: Context,
) : DataMigration<Preferences> {
  private var migratedFile: File? = null

  private val legacyFile: File?
    get() =
        listOf(
                context.dataStoreFile("smtp_prefs.pb"),
                File(context.filesDir, "smtp_prefs.pb"),
            )
            .firstOrNull(File::exists)

  override suspend fun shouldMigrate(currentData: Preferences): Boolean =
      legacyFile != null &&
          (currentData.isInitialPreferences() ||
              (currentData.emailsCount == 0 && currentData.matricesCount == 0))

  override suspend fun migrate(currentData: Preferences): Preferences {
    val file = legacyFile ?: return currentData
    val legacy = file.inputStream().use { LegacySmtpPreferences.parseFrom(it) }
    migratedFile = file

    val migrationBase =
        if (currentData.isInitialPreferences()) Preferences.getDefaultInstance() else currentData
    return migrationBase.toBuilder().addEmails(legacySmtpEmail(legacy)).build()
  }

  override suspend fun cleanUp() {
    migratedFile?.delete()
    migratedFile = null
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
