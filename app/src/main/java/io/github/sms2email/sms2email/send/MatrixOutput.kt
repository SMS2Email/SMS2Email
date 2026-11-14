package io.github.sms2email.sms2email.send

import android.content.Context
import io.github.sms2email.sms2email.CustomApplication
import io.github.sms2email.sms2email.preferences.MatrixPreference
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.matrix.android.sdk.api.auth.data.Credentials
import org.matrix.android.sdk.api.auth.data.HomeServerConnectionConfig
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.session.room.Room

suspend fun sendMatrix(context: Context, event: SmsEvent, matrix: MatrixPreference) {
  withContext(Dispatchers.IO) {
    val session = getMatrixSession(context, matrix)
    var room: Room? = null
    withTimeout(30_000) {
      while (room == null) {
        room = session.roomService().getRoom(matrix.roomId)
        if (room == null) delay(500)
      }
    }
    checkNotNull(room).sendService().sendTextMessage("SMS from ${event.sender}:\n${event.body}")
  }
}

private val sessionLock = Mutex()
private val sessions = ConcurrentHashMap<String, Session>()

fun rememberMatrixSession(matrix: MatrixPreference, session: Session) {
  sessions[sessionKey(matrix)] = session
}

suspend fun getMatrixSession(context: Context, matrix: MatrixPreference): Session =
    sessionLock.withLock {
      val key = sessionKey(matrix)
      sessions[key]?.let {
        return@withLock it
      }

      val application = context.applicationContext as CustomApplication
      val sdk = withContext(Dispatchers.Main) { application.matrix() }
      val homeServer =
          HomeServerConnectionConfig.Builder().withHomeServerUri(matrix.homeserver).build()
      val credentials =
          Credentials(
              userId = matrix.user,
              accessToken = matrix.accessToken,
              refreshToken = "",
              homeServer = matrix.homeserver,
              deviceId = matrix.deviceId,
          )
      val session = sdk.authenticationService().createSessionFromSso(homeServer, credentials)

      withContext(Dispatchers.Main) {
        session.open()
        session.syncService().startSync(true)
      }
      sessions[key] = session
      session
    }

private fun sessionKey(matrix: MatrixPreference): String =
    "${matrix.homeserver.trimEnd('/')}/${matrix.user}/${matrix.deviceId}/${matrix.accessToken}"
