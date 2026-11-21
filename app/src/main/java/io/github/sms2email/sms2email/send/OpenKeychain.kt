package io.github.sms2email.sms2email.send

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.IntentCompat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.openintents.openpgp.IOpenPgpService2
import org.openintents.openpgp.OpenPgpError
import org.openintents.openpgp.util.OpenPgpApi
import org.openintents.openpgp.util.OpenPgpServiceConnection

const val OPENKEYCHAIN_PACKAGE = "org.sufficientlysecure.keychain"
private const val BIND_TIMEOUT_MILLIS = 15_000L

/**
 * An OpenKeychain call failed. When [pendingIntent] is set, OpenKeychain needs the user (to allow
 * this app, pick a key or enter a passphrase); it can only be shown from the settings screen.
 */
class OpenKeychainException(
    message: String,
    val pendingIntent: PendingIntent? = null,
) : Exception(message)

/** Result of a call that may need the user: launch [NeedsUser] and pass its result data back. */
sealed interface OpenKeychainResult<out T> {
  data class Done<T>(val value: T) : OpenKeychainResult<T>

  data class NeedsUser(val pendingIntent: PendingIntent) : OpenKeychainResult<Nothing>
}

fun isOpenKeychainInstalled(context: Context): Boolean =
    try {
      context.packageManager.getPackageInfo(OPENKEYCHAIN_PACKAGE, 0)
      true
    } catch (_: PackageManager.NameNotFoundException) {
      false
    }

fun formatKeyId(keyId: Long) = "0x%016X".format(keyId)

/** Asks OpenKeychain for the recipients' keys. Pass a launched screen's result as [previous]. */
suspend fun selectRecipientKeys(
    context: Context,
    previous: Intent? = null,
): OpenKeychainResult<List<Long>> =
    call(context, (previous ?: Intent()).setAction(OpenPgpApi.ACTION_GET_KEY_IDS), null) {
        result ->
      result.getLongArrayExtra(OpenPgpApi.RESULT_KEY_IDS)?.toList()?.takeIf { it.isNotEmpty() }
          ?: throw OpenKeychainException("No recipient key was selected in OpenKeychain.")
    }

/** Asks OpenKeychain which of this phone's secret keys signs. Pass a screen's result as [previous]. */
suspend fun selectSigningKey(
    context: Context,
    previous: Intent? = null,
): OpenKeychainResult<Long> =
    call(context, (previous ?: Intent()).setAction(OpenPgpApi.ACTION_GET_SIGN_KEY_ID), null) {
        result ->
      result.getLongExtra(OpenPgpApi.EXTRA_SIGN_KEY_ID, 0L).takeIf { it != 0L }
          ?: throw OpenKeychainException("No signing key was selected in OpenKeychain.")
    }

/**
 * Signs [data] with [signKeyId] and encrypts it to [recipientKeyIds] in one OpenPGP message
 * (RFC 3156 section 6.2), returned ASCII-armored. [previous] is a launched screen's result.
 */
suspend fun signAndEncrypt(
    context: Context,
    data: ByteArray,
    recipientKeyIds: List<Long>,
    signKeyId: Long,
    previous: Intent? = null,
): OpenKeychainResult<String> {
  if (recipientKeyIds.isEmpty()) throw OpenKeychainException("No recipient key selected.")
  if (signKeyId == 0L) throw OpenKeychainException("No signing key selected.")
  val request =
      (previous ?: Intent())
          .setAction(OpenPgpApi.ACTION_SIGN_AND_ENCRYPT)
          .putExtra(OpenPgpApi.EXTRA_KEY_IDS, recipientKeyIds.toLongArray())
          .putExtra(OpenPgpApi.EXTRA_SIGN_KEY_ID, signKeyId)
          .putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true)
  val output = ByteArrayOutputStream()
  return call(context, request, ByteArrayInputStream(data), output) {
    val armored = output.toString("US-ASCII")
    if (!armored.contains("-----BEGIN PGP MESSAGE-----")) {
      throw OpenKeychainException("OpenKeychain returned no encrypted message.")
    }
    armored
  }
}

/** For background sending: anything that needs the user is a failure, so nothing is sent. */
suspend fun signAndEncryptInBackground(
    context: Context,
    data: ByteArray,
    recipientKeyIds: List<Long>,
    signKeyId: Long,
): String =
    when (val result = signAndEncrypt(context, data, recipientKeyIds, signKeyId)) {
      is OpenKeychainResult.Done -> result.value
      is OpenKeychainResult.NeedsUser ->
          throw OpenKeychainException(
              "OpenKeychain needs you (passphrase or permission). Open SMS2Email and tap " +
                  "\"Check encryption\".",
              result.pendingIntent,
          )
    }

private suspend fun <T> call(
    context: Context,
    request: Intent,
    input: InputStream?,
    output: ByteArrayOutputStream? = null,
    onSuccess: (Intent) -> T,
): OpenKeychainResult<T> =
    withOpenKeychain(context) { api ->
      val result = api.executeApi(request, input, output)
      when (result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
        OpenPgpApi.RESULT_CODE_SUCCESS -> OpenKeychainResult.Done(onSuccess(result))
        OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED ->
            OpenKeychainResult.NeedsUser(
                IntentCompat.getParcelableExtra(
                    result, OpenPgpApi.RESULT_INTENT, PendingIntent::class.java)
                    ?: throw OpenKeychainException("OpenKeychain asked for input but sent no screen."))
        else -> throw OpenKeychainException("OpenKeychain error: ${errorMessage(result)}")
      }
    }

/**
 * Binds to OpenKeychain, runs [block] on an IO thread, and always unbinds. Binding completes
 * asynchronously, so this waits for [OpenPgpServiceConnection.OnBound] before using the service.
 */
private suspend fun <T> withOpenKeychain(context: Context, block: (OpenPgpApi) -> T): T {
  val appContext = context.applicationContext
  if (!isOpenKeychainInstalled(appContext)) {
    throw OpenKeychainException("OpenKeychain is not installed.")
  }
  val bound = CompletableDeferred<IOpenPgpService2>()
  val connection =
      OpenPgpServiceConnection(
          appContext,
          OPENKEYCHAIN_PACKAGE,
          object : OpenPgpServiceConnection.OnBound {
            override fun onBound(service: IOpenPgpService2) {
              bound.complete(service)
            }

            override fun onError(e: Exception) {
              bound.completeExceptionally(
                  OpenKeychainException("Could not connect to OpenKeychain: ${e.message}"))
            }
          },
      )
  try {
    connection.bindToService()
    val service = withTimeout(BIND_TIMEOUT_MILLIS) { bound.await() }
    return withContext(Dispatchers.IO) { block(OpenPgpApi(appContext, service)) }
  } finally {
    // unbindService throws when the bind never succeeded.
    runCatching { connection.unbindFromService() }
  }
}

private fun errorMessage(result: Intent): String =
    IntentCompat.getParcelableExtra(result, OpenPgpApi.RESULT_ERROR, OpenPgpError::class.java)
        ?.message ?: "unknown error"
