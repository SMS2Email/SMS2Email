package io.github.sms2email.sms2email

import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import org.openintents.openpgp.IOpenPgpService2
import org.openintents.openpgp.OpenPgpError
import org.openintents.openpgp.util.OpenPgpApi
import org.openintents.openpgp.util.OpenPgpServiceConnection

class OpenPGPHelper(private val context: Context) {
  private val TAG = "OpenPGPHelper"
  private val OPENKEYCHAIN_PACKAGE = "org.sufficientlysecure.keychain"

  /**
   * Encrypts the given text using the specified PGP key IDs.
   * This is a synchronous call that should be run on a background thread.
   *
   * @param text The plaintext to encrypt
   * @param keyIds The recipient key IDs to encrypt for
   * @return The encrypted text, or null if encryption fails
   */
  fun encryptText(text: String, keyIds: List<Long>): String? {
    if (keyIds.isEmpty()) {
      Log.w(TAG, "No key IDs provided for encryption")
      return null
    }

    var result: String? = null
    val connection = OpenPgpServiceConnection(context, OPENKEYCHAIN_PACKAGE)

    try {
      connection.bindToService()

      val service: IOpenPgpService2? = connection.service
      if (service == null) {
        Log.e(TAG, "OpenKeychain service not available")
        return null
      }

      val api = OpenPgpApi(context, service)

      val data = Intent()
      data.action = OpenPgpApi.ACTION_ENCRYPT
      data.putExtra(OpenPgpApi.EXTRA_KEY_IDS, keyIds.toLongArray())
      data.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true)

      val inputStream: InputStream = ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))
      val outputStream = ByteArrayOutputStream()

      val resultIntent = api.executeApi(data, inputStream, outputStream)

      when (resultIntent.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
        OpenPgpApi.RESULT_CODE_SUCCESS -> {
          result = outputStream.toString("UTF-8")
          Log.d(TAG, "Successfully encrypted text")
        }
        OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED -> {
          Log.e(TAG, "User interaction required for encryption")
          val error = resultIntent.getParcelableExtra<OpenPgpError>(OpenPgpApi.RESULT_ERROR)
          Log.e(TAG, "Error: ${error?.message}")
        }
        OpenPgpApi.RESULT_CODE_ERROR -> {
          val error = resultIntent.getParcelableExtra<OpenPgpError>(OpenPgpApi.RESULT_ERROR)
          Log.e(TAG, "Encryption error: ${error?.message}")
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Exception during encryption", e)
    } finally {
      connection.unbindFromService()
    }

    return result
  }

  /**
   * Checks if OpenKeychain is installed on the device.
   */
  fun isOpenKeychainInstalled(): Boolean {
    return try {
      context.packageManager.getPackageInfo(OPENKEYCHAIN_PACKAGE, 0)
      true
    } catch (e: Exception) {
      false
    }
  }

  companion object {
    const val REQUEST_CODE_SELECT_KEY = 9527
    const val REQUEST_CODE_ENCRYPT = 9528

    /**
     * Creates an intent to select a public key from OpenKeychain.
     */
    fun createSelectKeyIntent(): Intent {
      val intent = Intent(OpenPgpApi.ACTION_GET_KEY_IDS)
      intent.setPackage("org.sufficientlysecure.keychain")
      return intent
    }
  }
}
