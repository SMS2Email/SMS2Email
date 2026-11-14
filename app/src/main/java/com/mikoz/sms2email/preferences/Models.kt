package io.github.sms2email.sms2email.preferences

import io.github.sms2email.sms2email.SmtpEncryptionMode

fun newEmailPreference() = EmailPreference(newId(), "Email", true)

fun newMatrixPreference() = MatrixPreference(newId(), "Matrix", true)

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
    val label: String,
    val enabled: Boolean,
    val emailTo: String = "",
    val smtpHost: String = "smtp.gmail.com",
    val smtpPort: Int = 465,
    val smtpUser: String = "",
    val smtpToken: String = "",
    val fromEmail: String = "",
    val encryptionMode: SmtpEncryptionMode = SmtpEncryptionMode.SMTP_ENCRYPTION_MODE_SMTPS,
) {
  fun isConfigured() =
      smtpHost.isNotBlank() &&
          smtpPort > 0 &&
          smtpUser.isNotBlank() &&
          smtpToken.isNotBlank() &&
          fromEmail.isNotBlank() &&
          emailTo.isNotBlank()
}

data class MatrixRoomOption(
    val id: String,
    val name: String,
)

data class MatrixPreference(
    val id: String,
    val label: String,
    val enabled: Boolean,
    val homeserver: String = "https://matrix.org",
    val user: String = "",
    val accessToken: String = "",
    val roomId: String = "",
    val deviceId: String = "",
) {
  fun isConfigured() =
      homeserver.startsWith("https://") &&
          user.isNotBlank() &&
          accessToken.isNotBlank() &&
          roomId.isNotBlank() &&
          deviceId.isNotBlank()
}

data class AppConfig(
    val inputsSelected: Boolean = false,
    val inputs: List<SimInput> = emptyList(),
    val emails: List<EmailPreference> = emptyList(),
    val matrix: List<MatrixPreference> = emptyList(),
)

data class AvailableSim(
    val slotIndex: Int,
    val subscriptionId: Int,
    val label: String,
)
