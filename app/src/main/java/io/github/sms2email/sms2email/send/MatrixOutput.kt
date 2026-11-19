package io.github.sms2email.sms2email.send

import android.content.Context
import io.github.sms2email.sms2email.preferences.MatrixPreference
import io.github.sms2email.sms2email.preferences.getConfig
import io.github.sms2email.sms2email.preferences.updateMatrix
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.ClientBuilder
import org.matrix.rustcomponents.sdk.ClientSessionDelegate
import org.matrix.rustcomponents.sdk.Session
import org.matrix.rustcomponents.sdk.SessionVerificationController
import org.matrix.rustcomponents.sdk.SlidingSyncVersion
import org.matrix.rustcomponents.sdk.SyncService
import org.matrix.rustcomponents.sdk.messageEventContentFromMarkdown

class MatrixSdkSession(
    val client: Client,
    val syncService: SyncService,
    val verificationController: SessionVerificationController,
)

private val sessionLock = Mutex()
private val sessions = ConcurrentHashMap<String, MatrixSdkSession>()

suspend fun newMatrixLoginClient(matrix: MatrixPreference): Client =
    ClientBuilder().serverNameOrHomeserverUrl(matrix.homeserver).build()

suspend fun forgetMatrixSession(matrixId: String) {
  val session = sessionLock.withLock { sessions.remove(matrixId) } ?: return
  session.verificationController.setDelegate(null)
  runCatching { session.syncService.stop() }
  session.syncService.close()
  session.verificationController.close()
  session.client.close()
}

suspend fun getMatrixSession(context: Context, matrix: MatrixPreference): MatrixSdkSession =
    sessionLock.withLock {
      sessions[matrix.id]?.let {
        return@withLock it
      }
      val applicationContext = context.applicationContext
      val client =
          ClientBuilder()
              .serverNameOrHomeserverUrl(matrix.homeserver)
              .sessionPaths(
                  storePath(applicationContext, matrix, "data"),
                  storePath(applicationContext, matrix, "cache"),
              )
              .setSessionDelegate(MatrixSessionDelegate(applicationContext, matrix))
              .build()
      try {
        client.restoreSession(matrix.toSession(client.homeserver()))
        val verificationController = client.getSessionVerificationController()
        val syncService = client.syncService().finish()
        syncService.start()
        MatrixSdkSession(client, syncService, verificationController).also {
          sessions[matrix.id] = it
        }
      } catch (exception: Exception) {
        client.close()
        throw exception
      }
    }

suspend fun sendMatrix(context: Context, event: SmsEvent, matrix: MatrixPreference) {
  val session = getMatrixSession(context, matrix)
  withContext(Dispatchers.IO) {
    val room =
        withTimeout(30_000) {
          while (true) {
            session.client.getRoom(matrix.roomId)?.let {
              return@withTimeout it
            }
            delay(500)
          }
          error("Unreachable")
        }
    try {
      room
          .timeline()
          .send(
              messageEventContentFromMarkdown("SMS from ${event.sender}:\n${event.body}"),
          )
    } finally {
      room.close()
    }
  }
}

private fun MatrixPreference.toSession(homeserverUrl: String) =
    Session(
        accessToken = accessToken,
        refreshToken = refreshToken.ifBlank { null },
        userId = user,
        deviceId = deviceId,
        homeserverUrl = homeserverUrl,
        oauthData = null,
        slidingSyncVersion = SlidingSyncVersion.NONE,
    )

private fun storePath(context: Context, matrix: MatrixPreference, kind: String): String {
  val accountKey = "${matrix.id}|${matrix.homeserver}|${matrix.user}|${matrix.deviceId}"
  val digest = MessageDigest.getInstance("SHA-256").digest(accountKey.toByteArray())
  val directory =
      File(
          File(context.filesDir, "matrix-rust"),
          digest.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') },
      )
  return File(directory, kind).apply { mkdirs() }.absolutePath
}

private class MatrixSessionDelegate(
    private val context: Context,
    private val matrix: MatrixPreference,
) : ClientSessionDelegate {
  override fun retrieveSessionFromKeychain(userId: String): Session =
      runBlocking(Dispatchers.IO) {
        val stored =
            getConfig(context).matrix.firstOrNull { it.id == matrix.id && it.user == userId }
                ?: matrix.takeIf { it.user == userId }
                ?: error("No stored Matrix session for $userId")
        stored.toSession(stored.homeserver)
      }

  override fun saveSessionInKeychain(session: Session) {
    runBlocking(Dispatchers.IO) {
      updateMatrix(context, matrix.id) {
        it.copy(
            homeserver = session.homeserverUrl,
            user = session.userId,
            accessToken = session.accessToken,
            refreshToken = session.refreshToken.orEmpty(),
            deviceId = session.deviceId,
        )
      }
    }
  }
}
