package io.github.sms2email.sms2email

import android.app.Application
import android.content.Intent
import kotlin.system.exitProcess
import org.matrix.android.sdk.api.Matrix
import org.matrix.android.sdk.api.MatrixConfiguration
import org.matrix.android.sdk.api.provider.RoomDisplayNameFallbackProvider

class CustomApplication : Application() {
  private val matrix by lazy {
    Matrix(
        context = this,
        matrixConfiguration =
            MatrixConfiguration(
                roomDisplayNameFallbackProvider = SimpleRoomDisplayNameFallbackProvider,
            ),
    )
  }

  override fun onCreate() {
    super.onCreate()
    setupCrashHandler()
    // The Matrix SDK owns WorkManager initialization.
    matrix()
  }

  fun matrix(): Matrix = matrix

  private fun setupCrashHandler() {
    val defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()

    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      try {
        val intent =
            Intent(this, CrashActivity::class.java).apply {
              putExtra("crash_message", throwable.toString())
              putExtra("stack_trace", getStackTrace(throwable))
              addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        startActivity(intent)
      } catch (e: Exception) {
        defaultUncaughtExceptionHandler?.uncaughtException(thread, throwable)
      }

      exitProcess(1)
    }
  }

  private fun getStackTrace(throwable: Throwable): String =
      java.io
          .StringWriter()
          .also { writer -> throwable.printStackTrace(java.io.PrintWriter(writer)) }
          .toString()
}

private object SimpleRoomDisplayNameFallbackProvider : RoomDisplayNameFallbackProvider {
  override fun excludedUserIds(roomId: String): List<String> = emptyList()

  override fun getNameForRoomInvite(): String = "Room invite"

  override fun getNameForEmptyRoom(isDirect: Boolean, leftMemberNames: List<String>): String =
      "Empty room"

  override fun getNameFor1member(name: String): String = name

  override fun getNameFor2members(name1: String, name2: String): String = "$name1 and $name2"

  override fun getNameFor3members(name1: String, name2: String, name3: String): String =
      "$name1, $name2 and $name3"

  override fun getNameFor4members(
      name1: String,
      name2: String,
      name3: String,
      name4: String,
  ): String = "$name1, $name2, $name3 and $name4"

  override fun getNameFor4membersAndMore(
      name1: String,
      name2: String,
      name3: String,
      remainingCount: Int,
  ): String = "$name1, $name2, $name3 and $remainingCount others"
}
