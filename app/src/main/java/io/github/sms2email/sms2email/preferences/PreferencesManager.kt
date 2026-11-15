package io.github.sms2email.sms2email.preferences

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.google.protobuf.InvalidProtocolBufferException
import io.github.sms2email.sms2email.EmailPreference as EmailProto
import io.github.sms2email.sms2email.InputPreference as InputProto
import io.github.sms2email.sms2email.MatrixPreference as MatrixProto
import io.github.sms2email.sms2email.Preferences
import io.github.sms2email.sms2email.SmtpEncryptionMode
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal const val DEFAULT_EMAIL_ID = "default-smtp"
internal const val DEFAULT_MATRIX_ID = "default-matrix"

internal val initialPreferences =
    Preferences.newBuilder()
        .addEmails(
            EmailProto.newBuilder()
                .setId(DEFAULT_EMAIL_ID)
                .setEnabled(true)
                .setSmtpHost("smtp.gmail.com")
                .setSmtpPort(465)
                .setEncryptionMode(SmtpEncryptionMode.SMTP_ENCRYPTION_MODE_SMTPS)
                .build())
        .addMatrices(
            MatrixProto.newBuilder()
                .setId(DEFAULT_MATRIX_ID)
                .setEnabled(true)
                .setHomeserver("https://matrix.org")
                .build())
        .build()

internal fun Preferences.isInitialPreferences() =
    emailsCount == 1 &&
        matricesCount == 1 &&
        emailsList.single().id == DEFAULT_EMAIL_ID &&
        matricesList.single().id == DEFAULT_MATRIX_ID

private val Context.preferencesDataStore: DataStore<Preferences> by
    dataStore(
        fileName = "preferences.pb",
        serializer = PreferencesSerializer,
        produceMigrations = { context ->
          listOf(LegacySmtpPreferencesMigration(context), LegacyPreferencesMigration)
        },
    )

object PreferencesSerializer : Serializer<Preferences> {
  override val defaultValue = initialPreferences

  override suspend fun readFrom(input: InputStream): Preferences =
      try {
        Preferences.parseFrom(input)
      } catch (exception: InvalidProtocolBufferException) {
        throw CorruptionException("Cannot read preferences.", exception)
      }

  override suspend fun writeTo(t: Preferences, output: OutputStream) = t.writeTo(output)
}

object PreferencesManager {
  fun configFlow(context: Context): Flow<AppConfig> =
      context.preferencesDataStore.data.map { prefs ->
        AppConfig(
            emails = prefs.emailsList.map { it.toModel() },
            matrix = prefs.matricesList.map { it.toModel() },
        )
      }

  suspend fun getConfig(context: Context): AppConfig = configFlow(context).first()

  suspend fun syncInputs(context: Context, available: List<AvailableSim>) {
    if (available.isEmpty()) return
    update(context) { prefs ->
      val emails =
          prefs.emailsList.map { email ->
            email
                .toBuilder()
                .clearInputs()
                .addAllInputs(email.inputsList.syncWith(available))
                .build()
          }
      val matrices =
          prefs.matricesList.map { matrix ->
            matrix
                .toBuilder()
                .clearInputs()
                .addAllInputs(matrix.inputsList.syncWith(available))
                .build()
          }
      prefs
          .toBuilder()
          .clearEmails()
          .addAllEmails(emails)
          .clearMatrices()
          .addAllMatrices(matrices)
          .build()
    }
  }

  suspend fun updateEmail(
      context: Context,
      id: String,
      update: (EmailPreference) -> EmailPreference,
  ) =
      update(context) { prefs ->
        val emails =
            prefs.emailsList.map { old ->
              val email = old.toModel()
              if (email.id == id) update(email).toProto() else old
            }
        prefs.toBuilder().clearEmails().addAllEmails(emails).build()
      }

  suspend fun addEmail(context: Context, email: EmailPreference) =
      update(context) { it.toBuilder().addEmails(email.toProto()).build() }

  suspend fun removeEmail(context: Context, id: String) =
      update(context) {
        it.toBuilder()
            .clearEmails()
            .addAllEmails(it.emailsList.filter { email -> email.id != id })
            .build()
      }

  suspend fun updateMatrix(
      context: Context,
      id: String,
      update: (MatrixPreference) -> MatrixPreference,
  ) =
      update(context) { prefs ->
        val matrices =
            prefs.matricesList.map { old ->
              val matrix = old.toModel()
              if (matrix.id == id) update(matrix).toProto() else old
            }
        prefs.toBuilder().clearMatrices().addAllMatrices(matrices).build()
      }

  suspend fun addMatrix(context: Context, matrix: MatrixPreference) =
      update(context) { it.toBuilder().addMatrices(matrix.toProto()).build() }

  suspend fun removeMatrix(context: Context, id: String) =
      update(context) {
        it.toBuilder()
            .clearMatrices()
            .addAllMatrices(it.matricesList.filter { matrix -> matrix.id != id })
            .build()
      }

  private suspend fun update(
      context: Context,
      transform: (Preferences) -> Preferences,
  ) {
    context.preferencesDataStore.updateData(transform)
  }

  private fun List<InputProto>.syncWith(available: List<AvailableSim>): List<InputProto> {
    val existing = associateBy { it.slotIndex }
    return available.map { sim ->
      val old = existing[sim.slotIndex]
      InputProto.newBuilder()
          .setId(old?.id ?: "slot-${sim.slotIndex}")
          .setSlotIndex(sim.slotIndex)
          .setSubscriptionId(sim.subscriptionId)
          .setLabel(sim.label)
          .setEnabled(old?.enabled ?: true)
          .build()
    }
  }

  private fun InputProto.toModel() =
      SimInput(
          id = id,
          slotIndex = slotIndex,
          subscriptionId = subscriptionId.takeIf { it >= 0 },
          label = label.ifBlank { "SIM ${slotIndex + 1}" },
          enabled = enabled,
      )

  private fun SimInput.toProto() =
      InputProto.newBuilder()
          .setId(id)
          .setSlotIndex(slotIndex)
          .setSubscriptionId(subscriptionId ?: -1)
          .setLabel(label)
          .setEnabled(enabled)
          .build()

  private fun EmailProto.toModel() =
      EmailPreference(
          id = id,
          enabled = enabled,
          emailTo = emailTo,
          smtpHost = smtpHost.ifBlank { "smtp.gmail.com" },
          smtpPort = smtpPort.takeIf { it > 0 } ?: 465,
          smtpUser = smtpUser,
          smtpPassword = smtpPassword,
          fromEmail = fromEmail,
          encryptionMode = encryptionMode.validEncryption(),
          inputs = inputsList.map { it.toModel() },
      )

  private fun EmailPreference.toProto() =
      EmailProto.newBuilder()
          .setId(id)
          .setEnabled(enabled)
          .setEmailTo(emailTo)
          .setSmtpHost(smtpHost)
          .setSmtpPort(smtpPort)
          .setSmtpUser(smtpUser)
          .setSmtpPassword(smtpPassword)
          .setFromEmail(fromEmail)
          .setEncryptionMode(encryptionMode)
          .addAllInputs(inputs.map { it.toProto() })
          .build()

  private fun MatrixProto.toModel() =
      MatrixPreference(
          id = id,
          enabled = enabled,
          homeserver = homeserver.ifBlank { "https://matrix.org" },
          user = user,
          accessToken = accessToken,
          roomId = roomId,
          deviceId = deviceId,
          inputs = inputsList.map { it.toModel() },
      )

  private fun MatrixPreference.toProto() =
      MatrixProto.newBuilder()
          .setId(id)
          .setEnabled(enabled)
          .setHomeserver(homeserver)
          .setUser(user)
          .setAccessToken(accessToken)
          .setRoomId(roomId)
          .setDeviceId(deviceId)
          .addAllInputs(inputs.map { it.toProto() })
          .build()

  private fun SmtpEncryptionMode.validEncryption() =
      takeUnless { it == SmtpEncryptionMode.UNRECOGNIZED }
          ?: SmtpEncryptionMode.SMTP_ENCRYPTION_MODE_SMTPS
}
