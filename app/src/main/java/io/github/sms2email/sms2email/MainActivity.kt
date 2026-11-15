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
import io.github.sms2email.sms2email.send.getMatrixSession
import io.github.sms2email.sms2email.send.rememberMatrixSession
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
import org.matrix.android.sdk.api.auth.SSOAction
import org.matrix.android.sdk.api.auth.data.HomeServerConnectionConfig
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.session.room.RoomSortOrder
import org.matrix.android.sdk.api.session.room.model.Membership
import org.matrix.android.sdk.api.session.room.roomSummaryQueryParams

class MainActivity : ComponentActivity() {
  private val smsPermissionGranted = MutableStateFlow(false)
  private val phonePermissionGranted = MutableStateFlow(false)
  private val notificationPermissionGranted = MutableStateFlow(false)
  private val matrixRooms = MutableStateFlow<Map<String, List<MatrixRoomOption>>>(emptyMap())
  private val matrixLoading = MutableStateFlow<Set<String>>(emptySet())
  private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var matrixLoginJob: Job? = null
  private var pendingMatrix: MatrixPreference? = null
  private var pendingHomeserver: String? = null
  private var handledMatrixLoginToken: String? = null

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
                onSelectMatrixRoom = { id, roomId ->
                  activityScope.launch {
                    updateMatrix(this@MainActivity, id) { it.copy(roomId = roomId) }
                  }
                },
                modifier = Modifier.fillMaxSize(),
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
    activityScope.cancel()
    super.onDestroy()
  }

  private fun connectMatrix(matrix: MatrixPreference) {
    matrixLoginJob?.cancel()
    handledMatrixLoginToken = null
    pendingMatrix = matrix
    pendingHomeserver = matrix.homeserver
    matrixLoginJob =
        activityScope.launch {
          try {
            val application = application as CustomApplication
            val homeServer =
                HomeServerConnectionConfig.Builder()
                    .withHomeServerUri(matrix.homeserver.trimEnd('/'))
                    .build()
            val authentication = application.matrix().authenticationService()
            val flow = withContext(Dispatchers.IO) { authentication.getLoginFlow(homeServer) }
            check(flow.supportedLoginTypes.contains("m.login.sso")) {
              "This homeserver does not provide Matrix SSO."
            }
            val url =
                authentication.getSsoUrl(
                    MATRIX_SSO_REDIRECT,
                    null,
                    null,
                    SSOAction.LOGIN,
                )
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
          } catch (exception: Exception) {
            Toast.makeText(
                    this@MainActivity,
                    exception.message ?: "Unable to start Matrix login",
                    Toast.LENGTH_LONG,
                )
                .show()
          }
        }
  }

  private fun handleMatrixRedirect(intent: Intent?) {
    val data = intent?.data ?: return
    if (data.scheme != "sms2email" || data.host != "matrix" || data.path != "/sso") return
    val loginToken = data.getQueryParameter("loginToken")
    if (loginToken.isNullOrBlank()) {
      Toast.makeText(this, "Matrix browser login did not return a token", Toast.LENGTH_LONG).show()
      return
    }
    if (loginToken == handledMatrixLoginToken) return
    handledMatrixLoginToken = loginToken
    pendingMatrix?.id?.let { matrixLoading.value = matrixLoading.value + it }
    matrixLoginJob?.cancel()
    matrixLoginJob = activityScope.launch { finishMatrixLogin(loginToken) }
  }

  private suspend fun finishMatrixLogin(loginToken: String) {
    val matrix = pendingMatrix ?: return
    val matrixId = matrix.id
    val homeserver = pendingHomeserver ?: return
    var loginSession: Session? = null
    var sessionCached = false
    try {
      val application = application as CustomApplication
      val authentication = application.matrix().authenticationService()
      val session =
          withContext(Dispatchers.IO) { authentication.getLoginWizard().loginWithToken(loginToken) }
      loginSession = session
      withContext(Dispatchers.Main) {
        session.open()
        session.syncService().startSync(true)
      }
      val rooms = loadJoinedRooms(session)
      val credentials = session.sessionParams.credentials
      var connectedMatrix: MatrixPreference? = null
      updateMatrix(this, matrixId) {
        it.copy(
                homeserver = homeserver,
                user = credentials.userId,
                accessToken = credentials.accessToken,
                roomId = "",
                deviceId = credentials.deviceId,
            )
            .also { connectedMatrix = it }
      }
      rememberMatrixSession(connectedMatrix ?: error("Matrix preference disappeared"), session)
      sessionCached = true
      matrixRooms.value = matrixRooms.value + (matrixId to rooms)
      Toast.makeText(this, "Matrix connected. Select a room.", Toast.LENGTH_SHORT).show()
    } catch (exception: Exception) {
      Toast.makeText(
              this,
              exception.message ?: "Matrix browser login failed",
              Toast.LENGTH_LONG,
          )
          .show()
    } finally {
      if (!sessionCached) {
        loginSession?.let { session -> withContext(Dispatchers.Main) { session.close() } }
      }
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
          val rooms = loadJoinedRooms(getMatrixSession(this@MainActivity, matrix))
          matrixRooms.value = matrixRooms.value + (matrix.id to rooms)
        } catch (_: Exception) {
          // Keep the sender visible; the UI will explain that no rooms are available.
        } finally {
          matrixLoading.value = matrixLoading.value - matrix.id
        }
      }
    }
  }

  private suspend fun loadJoinedRooms(session: Session): List<MatrixRoomOption> =
      withTimeout(TimeUnit.SECONDS.toMillis(30)) {
        var options = emptyList<MatrixRoomOption>()
        while (options.isEmpty()) {
          val summaries =
              withContext(Dispatchers.IO) {
                session
                    .roomService()
                    .getRoomSummaries(
                        roomSummaryQueryParams { memberships = listOf(Membership.JOIN) },
                        RoomSortOrder.NAME,
                    )
              }
          options =
              summaries.map { summary ->
                val displayName = summary.displayName.orEmpty()
                val canonicalAlias = summary.canonicalAlias.orEmpty()
                MatrixRoomOption(
                    id = summary.roomId,
                    name = displayName.ifBlank { canonicalAlias.ifBlank { summary.roomId } },
                )
              }
          if (options.isEmpty()) delay(500)
        }
        options
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
