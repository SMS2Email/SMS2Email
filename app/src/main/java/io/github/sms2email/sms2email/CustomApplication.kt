package io.github.sms2email.sms2email

import android.app.Application
import android.content.Intent
import kotlin.system.exitProcess
import org.matrix.rustcomponents.sdk.LogLevel
import org.matrix.rustcomponents.sdk.TracingConfiguration
import org.matrix.rustcomponents.sdk.initPlatform
import org.matrix.rustsdk.Android

class CustomApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    setupCrashHandler()
    Android.init(this)
    initPlatform(
        TracingConfiguration(
            logLevel = LogLevel.WARN,
            traceLogPacks = emptyList(),
            extraTargets = emptyList(),
            writeToStdoutOrSystem = true,
            writeToFiles = null,
            sentryConfig = null,
        ),
        false,
    )
  }

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
