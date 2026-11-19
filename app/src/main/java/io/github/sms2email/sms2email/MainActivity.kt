package io.github.sms2email.sms2email

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import io.github.sms2email.sms2email.preferences.MatrixPreference
import io.github.sms2email.sms2email.preferences.MatrixRoomOption
import io.github.sms2email.sms2email.preferences.getConfig
import io.github.sms2email.sms2email.preferences.updateMatrix
import io.github.sms2email.sms2email.send.MatrixSdkSession
import io.github.sms2email.sms2email.send.forgetMatrixSession
import io.github.sms2email.sms2email.send.getMatrixSession
import io.github.sms2email.sms2email.send.newMatrixLoginClient
import io.github.sms2email.sms2email.ui.MatrixSasEmoji
import io.github.sms2email.sms2email.ui.MatrixVerificationDialog
import io.github.sms2email.sms2email.ui.MatrixVerificationPrompt
import io.github.sms2email.sms2email.ui.SettingsScreen
import io.github.sms2email.sms2email.ui.theme.SMS2EmailTheme
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.Membership
import org.matrix.rustcomponents.sdk.SessionVerificationController
import org.matrix.rustcomponents.sdk.SessionVerificationControllerDelegate
import org.matrix.rustcomponents.sdk.SessionVerificationData
import org.matrix.rustcomponents.sdk.SsoHandler

class MainActivity : ComponentActivity() {
  private val smsPermissionGranted = MutableStateFlow(false)
  private val phonePermissionGranted = MutableStateFlow(false)
  private val notificationPermissionGranted = MutableStateFlow(false)
  private val matrixRooms = MutableStateFlow<Map<String, List<MatrixRoomOption>>>(emptyMap())
  private val matrixLoading = MutableStateFlow<Set<String>>(emptySet())
  private val matrixVerification = MutableStateFlow<MatrixVerificationPrompt?>(null)
  private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val verificationControllers = mutableSetOf<SessionVerificationController>()
  private var matrixLoginJob: Job? = null
  private var pendingMatrix: MatrixPreference? = null
  private var pendingLoginClient: Client? = null
  private var pendingSsoHandler: SsoHandler? = null
  private var handledMatrixCallback: String? = null

  private val smsPermissionLauncher =
      registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(this, "SMS permission denied", Toast.LENGTH_SHORT).show()
        smsPermissionGranted.value = granted
      }

  private val phonePermissionLauncher =
      registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(this, "Phone permission denied", Toast.LENGTH_SHORT).show()
        phonePermissionGranted.value = granted
      }

  private val notificationPermissionLauncher =
      registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted)
            Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
        notificationPermissionGranted.value = granted
      }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    smsPermissionGranted.value = hasPermission(Manifest.permission.RECEIVE_SMS)
    phonePermissionGranted.value = hasPermission(Manifest.permission.READ_PHONE_STATE)
    notificationPermissionGranted.value = hasNotificationPermission()

    setContent {
      SMS2EmailTheme {
        val isDark = isSystemInDarkTheme()
        val smsGranted by smsPermissionGranted.collectAsState()
        val phoneGranted by phonePermissionGranted.collectAsState()
        val notificationsGranted by notificationPermissionGranted.collectAsState()
        val rooms by matrixRooms.collectAsState()
        val loadingMatrix by matrixLoading.collectAsState()
        val verification by matrixVerification.collectAsState()
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        ) {
          if (isDark) {
            Image(
                painter = painterResource(id = R.drawable.background_dark),
                contentDescription = "Background",
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                contentScale = ContentScale.FillWidth,
                alpha = 0.3f,
            )
          }
          Surface(
              modifier = Modifier.fillMaxSize(),
              color = Color.Transparent,
              contentColor = MaterialTheme.colorScheme.onBackground,
          ) {
            SettingsScreen(
                context = this@MainActivity,
                isSmsPermissionGranted = smsGranted,
                onRequestSmsPermission = {
                  smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                },
                isPhonePermissionGranted = phoneGranted,
                onRequestPhonePermission = {
                  phonePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                },
                isNotificationPermissionGranted = notificationsGranted,
                onRequestNotificationPermission = {
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                  }
                },
                matrixRooms = rooms,
                loadingMatrix = loadingMatrix,
                onConnectMatrix = ::connectMatrix,
                onVerifyMatrix = ::verifyMatrix,
                onSelectMatrixRoom = { id, roomId ->
                  activityScope.launch {
                    updateMatrix(this@MainActivity, id) { it.copy(roomId = roomId) }
                  }
                },
                modifier = Modifier.fillMaxSize(),
            )
          }
          verification?.let { prompt ->
            MatrixVerificationDialog(
                prompt = prompt,
                onAccept = { acceptVerification(prompt) },
                onReject = { cancelVerification(prompt) },
                onConfirm = { confirmVerification(prompt) },
                onMismatch = { mismatchVerification(prompt) },
                onCancel = { cancelVerification(prompt) },
                onDone = { matrixVerification.value = null },
            )
          }
        }
      }
    }
    handleMatrixRedirect(intent)
    loadPersistedMatrixRooms()
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleMatrixRedirect(intent)
  }

  override fun onDestroy() {
    verificationControllers.forEach { it.setDelegate(null) }
    verificationControllers.clear()
    activityScope.cancel()
    super.onDestroy()
  }

  private fun connectMatrix(matrix: MatrixPreference) {
    matrixLoginJob?.cancel()
    clearPendingLogin()
    handledMatrixCallback = null
    pendingMatrix = matrix
    matrixLoginJob =
        activityScope.launch {
          var loginClient: Client? = null
          var ssoHandler: SsoHandler? = null
          try {
            val client = withContext(Dispatchers.IO) { newMatrixLoginClient(matrix) }
            loginClient = client
            val handler =
                withContext(Dispatchers.IO) { client.startSsoLogin(MATRIX_SSO_REDIRECT, null) }
            ssoHandler = handler
            pendingLoginClient = client
            pendingSsoHandler = handler
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(handler.url())))
            loginClient = null
            ssoHandler = null
          } catch (exception: Exception) {
            clearPendingLogin()
            Toast.makeText(
                    this@MainActivity,
                    exception.message ?: "Unable to start Matrix login",
                    Toast.LENGTH_LONG,
                )
                .show()
          } finally {
            ssoHandler?.close()
            loginClient?.close()
          }
        }
  }

  private fun handleMatrixRedirect(intent: Intent?) {
    val callbackUrl =
        intent
            ?.data
            ?.takeIf { it.scheme == "sms2email" && it.host == "matrix" && it.path == "/sso" }
            ?.toString() ?: return
    if (callbackUrl == handledMatrixCallback) return
    if (pendingLoginClient == null || pendingSsoHandler == null) {
      Toast.makeText(this, "Matrix login expired. Please sign in again.", Toast.LENGTH_LONG).show()
      return
    }
    handledMatrixCallback = callbackUrl
    pendingMatrix?.id?.let { matrixLoading.value = matrixLoading.value + it }
    matrixLoginJob = activityScope.launch { finishMatrixLogin(callbackUrl) }
  }

  private fun clearPendingLogin() {
    pendingSsoHandler?.close()
    pendingLoginClient?.close()
    pendingSsoHandler = null
    pendingLoginClient = null
  }

  private suspend fun finishMatrixLogin(callbackUrl: String) {
    val matrix = pendingMatrix
    val loginClient = pendingLoginClient
    val ssoHandler = pendingSsoHandler
    pendingLoginClient = null
    pendingSsoHandler = null
    if (matrix == null || loginClient == null || ssoHandler == null) {
      ssoHandler?.close()
      loginClient?.close()
      return
    }
    val matrixId = matrix.id
    try {
      val session =
          withContext(Dispatchers.IO) {
            ssoHandler.finish(callbackUrl)
            loginClient.session()
          }
      forgetMatrixSession(matrixId)
      var connectedMatrix: MatrixPreference? = null
      updateMatrix(this, matrixId) {
        it.copy(
                homeserver = session.homeserverUrl,
                user = session.userId,
                accessToken = session.accessToken,
                refreshToken = session.refreshToken.orEmpty(),
                roomId = "",
                deviceId = session.deviceId,
            )
            .also { connectedMatrix = it }
      }
      val savedMatrix = connectedMatrix ?: error("Matrix preference disappeared")
      val matrixSession = getMatrixSession(this, savedMatrix)
      attachVerificationDelegate(matrixSession)
      val rooms = loadJoinedRooms(matrixSession)
      matrixRooms.value = matrixRooms.value + (matrixId to rooms)
      Toast.makeText(this, "Matrix connected. Select a room.", Toast.LENGTH_SHORT).show()
      requestDeviceVerification(matrixSession)
    } catch (exception: Exception) {
      Toast.makeText(
              this,
              exception.message ?: "Matrix browser login failed",
              Toast.LENGTH_LONG,
          )
          .show()
    } finally {
      ssoHandler.close()
      loginClient.close()
      matrixLoading.value = matrixLoading.value - matrixId
    }
  }

  private fun loadPersistedMatrixRooms() {
    activityScope.launch {
      val matrices =
          try {
            getConfig(this@MainActivity).matrix.filter {
              it.homeserver.startsWith("https://") &&
                  it.user.isNotBlank() &&
                  it.accessToken.isNotBlank() &&
                  it.deviceId.isNotBlank()
            }
          } catch (_: Exception) {
            emptyList()
          }
      matrices.forEach { matrix ->
        matrixLoading.value = matrixLoading.value + matrix.id
        try {
          val session = getMatrixSession(this@MainActivity, matrix)
          attachVerificationDelegate(session)
          val rooms = loadJoinedRooms(session)
          matrixRooms.value = matrixRooms.value + (matrix.id to rooms)
        } catch (_: Exception) {
          // Keep the sender visible; the UI will explain that no rooms are available.
        } finally {
          matrixLoading.value = matrixLoading.value - matrix.id
        }
      }
    }
  }

  private suspend fun loadJoinedRooms(session: MatrixSdkSession): List<MatrixRoomOption> =
      withTimeout(TimeUnit.SECONDS.toMillis(30)) {
        while (true) {
          val rooms = withContext(Dispatchers.IO) { session.client.rooms() }
          val options =
              try {
                rooms.mapNotNull { room ->
                  if (room.membership() != Membership.JOINED) return@mapNotNull null
                  val name = room.displayName().orEmpty()
                  MatrixRoomOption(
                      id = room.id(),
                      name = name.ifBlank { room.canonicalAlias().orEmpty().ifBlank { room.id() } },
                  )
                }
              } finally {
                rooms.forEach { it.close() }
              }
          if (options.isNotEmpty()) return@withTimeout options
          delay(500)
        }
        emptyList()
      }

  private fun verifyMatrix(matrix: MatrixPreference) {
    activityScope.launch {
      try {
        val session = getMatrixSession(this@MainActivity, matrix)
        attachVerificationDelegate(session)
        requestDeviceVerification(session)
      } catch (exception: Exception) {
        Toast.makeText(
                this@MainActivity,
                exception.message ?: "Unable to start device verification",
                Toast.LENGTH_LONG,
            )
            .show()
      }
    }
  }

  private suspend fun requestDeviceVerification(session: MatrixSdkSession) {
    matrixVerification.value =
        MatrixVerificationPrompt.Waiting(
            session.verificationController,
            "Sending a verification request to your other Matrix device…",
        )
    try {
      session.verificationController.requestDeviceVerification()
      matrixVerification.value =
          MatrixVerificationPrompt.Waiting(
              session.verificationController,
              "Waiting for your other Matrix device to accept the request…",
          )
    } catch (exception: Exception) {
      matrixVerification.value =
          MatrixVerificationPrompt.Failed(
              session.verificationController,
              exception.message ?: "Unable to request device verification.",
          )
    }
  }

  private fun attachVerificationDelegate(session: MatrixSdkSession) {
    val controller = session.verificationController
    if (!verificationControllers.add(controller)) return
    controller.setDelegate(
        object : SessionVerificationControllerDelegate {
          override fun didReceiveVerificationRequest(
              details: org.matrix.rustcomponents.sdk.SessionVerificationRequestDetails,
          ) {
            activityScope.launch {
              matrixVerification.value = MatrixVerificationPrompt.Incoming(controller, details)
            }
          }

          override fun didAcceptVerificationRequest() {
            activityScope.launch {
              try {
                controller.startSasVerification()
                matrixVerification.value =
                    MatrixVerificationPrompt.Waiting(controller, "Starting emoji comparison…")
              } catch (exception: Exception) {
                matrixVerification.value =
                    MatrixVerificationPrompt.Failed(
                        controller,
                        exception.message ?: "Unable to start emoji comparison.",
                    )
              }
            }
          }

          override fun didStartSasVerification() {
            activityScope.launch {
              matrixVerification.value =
                  MatrixVerificationPrompt.Waiting(controller, "Preparing the security code…")
            }
          }

          override fun didReceiveVerificationData(data: SessionVerificationData) {
            val prompt =
                when (data) {
                  is SessionVerificationData.Emojis ->
                      MatrixVerificationPrompt.Emojis(
                          controller,
                          data.emojis.map { MatrixSasEmoji(it.symbol(), it.description()) },
                      )
                  is SessionVerificationData.Decimals ->
                      MatrixVerificationPrompt.Decimals(
                          controller,
                          data.values.map { it.toInt() },
                      )
                }
            data.destroy()
            activityScope.launch { matrixVerification.value = prompt }
          }

          override fun didFail() {
            activityScope.launch {
              matrixVerification.value =
                  MatrixVerificationPrompt.Failed(controller, "Device verification failed.")
            }
          }

          override fun didCancel() {
            activityScope.launch {
              matrixVerification.value =
                  MatrixVerificationPrompt.Failed(
                      controller, "The other device cancelled verification.")
            }
          }

          override fun didFinish() {
            activityScope.launch {
              matrixVerification.value = MatrixVerificationPrompt.Finished(controller)
            }
          }
        },
    )
  }

  private fun acceptVerification(prompt: MatrixVerificationPrompt) {
    val incoming = prompt as? MatrixVerificationPrompt.Incoming ?: return
    activityScope.launch {
      try {
        incoming.controller.acknowledgeVerificationRequest(
            incoming.details.senderProfile.userId,
            incoming.details.flowId,
        )
        incoming.controller.acceptVerificationRequest()
        matrixVerification.value =
            MatrixVerificationPrompt.Waiting(
                incoming.controller,
                "Waiting for the other device to start emoji comparison…",
            )
      } catch (exception: Exception) {
        showVerificationError(incoming.controller, exception)
      }
    }
  }

  private fun confirmVerification(prompt: MatrixVerificationPrompt) {
    if (prompt !is MatrixVerificationPrompt.Emojis && prompt !is MatrixVerificationPrompt.Decimals)
        return
    activityScope.launch {
      try {
        prompt.controller.approveVerification()
        matrixVerification.value =
            MatrixVerificationPrompt.Waiting(prompt.controller, "Finishing device verification…")
      } catch (exception: Exception) {
        showVerificationError(prompt.controller, exception)
      }
    }
  }

  private fun mismatchVerification(prompt: MatrixVerificationPrompt) {
    if (prompt !is MatrixVerificationPrompt.Emojis && prompt !is MatrixVerificationPrompt.Decimals)
        return
    activityScope.launch {
      try {
        prompt.controller.declineVerification()
        matrixVerification.value =
            MatrixVerificationPrompt.Failed(prompt.controller, "The security codes did not match.")
      } catch (exception: Exception) {
        showVerificationError(prompt.controller, exception)
      }
    }
  }

  private fun cancelVerification(prompt: MatrixVerificationPrompt) {
    activityScope.launch {
      try {
        if (prompt is MatrixVerificationPrompt.Incoming) {
          prompt.controller.acknowledgeVerificationRequest(
              prompt.details.senderProfile.userId,
              prompt.details.flowId,
          )
        }
        prompt.controller.cancelVerification()
      } catch (_: Exception) {
        // The request may already have been cancelled by the other device.
      } finally {
        matrixVerification.value = null
      }
    }
  }

  private fun showVerificationError(
      controller: SessionVerificationController,
      exception: Exception,
  ) {
    matrixVerification.value =
        MatrixVerificationPrompt.Failed(
            controller,
            exception.message ?: "Device verification failed.",
        )
  }

  private fun hasPermission(permission: String): Boolean =
      ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

  private fun hasNotificationPermission(): Boolean =
      Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
          hasPermission(Manifest.permission.POST_NOTIFICATIONS)

  private companion object {
    const val MATRIX_SSO_REDIRECT = "sms2email://matrix/sso"
  }
}
