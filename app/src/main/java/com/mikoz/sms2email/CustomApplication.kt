package io.github.sms2email.sms2email

import android.app.Application
import android.content.Intent
import kotlin.system.exitProcess

class CustomApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    setupCrashHandler()
  }

  private fun setupCrashHandler() {
    val defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()

    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      try {
        // Create intent to start crash activity
        val intent =
            Intent(this, CrashActivity::class.java).apply {
              putExtra("crash_message", throwable.toString())
              putExtra("stack_trace", getStackTrace(throwable))
              addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        startActivity(intent)
      } catch (e: Exception) {
        // Fallback to default handler
        defaultUncaughtExceptionHandler?.uncaughtException(thread, throwable)
      }

      exitProcess(1)
    }
  }

  private fun getStackTrace(throwable: Throwable): String {
    val sw = java.io.StringWriter()
    val pw = java.io.PrintWriter(sw)
    throwable.printStackTrace(pw)
    return sw.toString()
  }
}
