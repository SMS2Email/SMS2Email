package io.github.sms2email.sms2email

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

val Context.smtpDataStore: DataStore<SmtpPreferences> by
    dataStore(
        fileName = "smtp_prefs.pb",
        serializer = SmtpPreferencesSerializer,
    )

data class SmtpConfig(
    val smtpHost: String = "smtp.gmail.com",
    val smtpPort: Int = 465,
    val smtpUser: String = "",
    val smtpPassword: String = "",
    val fromEmail: String = "",
    val toEmail: String = "",
    val encryptionMode: SmtpEncryptionMode = SmtpEncryptionMode.SMTP_ENCRYPTION_MODE_SMTPS,
    val enabled: Boolean = true,
)

object SmtpPreferencesSerializer : Serializer<SmtpPreferences> {
  override val defaultValue: SmtpPreferences = SmtpPreferences.getDefaultInstance()

  override suspend fun readFrom(input: InputStream): SmtpPreferences =
      try {
        SmtpPreferences.parseFrom(input)
      } catch (exception: InvalidProtocolBufferException) {
        throw CorruptionException("Cannot read proto.", exception)
      }

  override suspend fun writeTo(
      t: SmtpPreferences,
      output: OutputStream,
  ) = t.writeTo(output)
}

object PreferencesManager {
  val defaultConfig = SmtpConfig()

  fun smtpConfigFlow(context: Context): Flow<SmtpConfig> =
      context.smtpDataStore.data.map { prefs ->
        val port = prefs.smtpPort
        SmtpConfig(
            smtpHost = prefs.smtpHost.takeIf { it.isNotBlank() } ?: defaultConfig.smtpHost,
            smtpPort = if (port > 0) port else defaultConfig.smtpPort,
            smtpUser = prefs.smtpUser,
            smtpPassword = prefs.smtpPassword,
            fromEmail = prefs.fromEmail,
            toEmail = prefs.toEmail,
            encryptionMode =
                prefs.encryptionMode.takeIf { it != SmtpEncryptionMode.UNRECOGNIZED }
                    ?: defaultConfig.encryptionMode,
            enabled =
                when (prefs.forwardingState) {
                  ForwardingState.FORWARDING_STATE_DISABLED -> false
                  ForwardingState.FORWARDING_STATE_ENABLED -> true
                  else -> defaultConfig.enabled // UNSET defaults to enabled
                },
        )
      }

  suspend fun updateSmtpHost(
      context: Context,
      value: String,
  ) {
    context.smtpDataStore.updateData { it.toBuilder().setSmtpHost(value).build() }
  }

  suspend fun updateSmtpPort(
      context: Context,
      value: Int,
  ) {
    context.smtpDataStore.updateData { it.toBuilder().setSmtpPort(value).build() }
  }

  suspend fun updateSmtpUser(
      context: Context,
      value: String,
  ) {
    context.smtpDataStore.updateData { it.toBuilder().setSmtpUser(value).build() }
  }

  suspend fun updateSmtpPassword(
      context: Context,
      value: String,
  ) {
    context.smtpDataStore.updateData { it.toBuilder().setSmtpPassword(value).build() }
  }

  suspend fun updateFromEmail(
      context: Context,
      value: String,
  ) {
    context.smtpDataStore.updateData { it.toBuilder().setFromEmail(value).build() }
  }

  suspend fun updateToEmail(
      context: Context,
      value: String,
  ) {
    context.smtpDataStore.updateData { it.toBuilder().setToEmail(value).build() }
  }

  suspend fun updateEncryptionMode(
      context: Context,
      value: SmtpEncryptionMode,
  ) {
    context.smtpDataStore.updateData { it.toBuilder().setEncryptionMode(value).build() }
  }

  suspend fun updateEnabled(
      context: Context,
      value: Boolean,
  ) {
    val state =
        if (value) ForwardingState.FORWARDING_STATE_ENABLED
        else ForwardingState.FORWARDING_STATE_DISABLED
    context.smtpDataStore.updateData { it.toBuilder().setForwardingState(state).build() }
  }

  suspend fun getConfig(context: Context): SmtpConfig = smtpConfigFlow(context).first()

  @JvmStatic
  fun getConfigBlocking(context: Context): SmtpConfig = runBlocking { getConfig(context) }
}
