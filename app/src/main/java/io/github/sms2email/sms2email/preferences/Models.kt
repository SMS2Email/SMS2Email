package io.github.sms2email.sms2email.preferences

import io.github.sms2email.sms2email.SmtpEncryptionMode

fun newEmailPreference() = EmailPreference(newId(), true)

fun newMatrixPreference() = MatrixPreference(newId(), true)

private fun newId() = java.util.UUID.randomUUID().toString()

data class SimInput(
    val id: String,
    val slotIndex: Int,
    val subscriptionId: Int?,
    val label: String,
    val enabled: Boolean = true,
)

data class EmailPreference(
    val id: String,
    val enabled: Boolean,
    val emailTo: String = "",
    val smtpHost: String = "smtp.gmail.com",
    val smtpPort: Int = 465,
    val smtpUser: String = "",
    val smtpPassword: String = "",
    val fromEmail: String = "",
    val encryptionMode: SmtpEncryptionMode = SmtpEncryptionMode.SMTP_ENCRYPTION_MODE_SMTPS,
    val inputs: List<SimInput> = emptyList(),
) {
  fun isConfigured() =
      smtpHost.isNotBlank() &&
          smtpPort > 0 &&
          smtpUser.isNotBlank() &&
          smtpPassword.isNotBlank() &&
          fromEmail.isNotBlank() &&
          emailTo.isNotBlank()
}

data class MatrixRoomOption(
    val id: String,
    val name: String,
)

data class MatrixPreference(
    val id: String,
    val enabled: Boolean,
    val homeserver: String = "https://matrix.org",
    val user: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    val roomId: String = "",
    val deviceId: String = "",
    val inputs: List<SimInput> = emptyList(),
) {
  fun isConfigured() =
      homeserver.startsWith("https://") &&
          user.isNotBlank() &&
          accessToken.isNotBlank() &&
          roomId.isNotBlank() &&
          deviceId.isNotBlank()
}

data class AppConfig(
    val emails: List<EmailPreference> = emptyList(),
    val matrix: List<MatrixPreference> = emptyList(),
)

data class AvailableSim(
    val slotIndex: Int,
    val subscriptionId: Int,
    val label: String,
)
