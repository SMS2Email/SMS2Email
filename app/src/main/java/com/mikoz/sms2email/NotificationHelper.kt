package io.github.sms2email.sms2email

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

const val NOTIFICATION_CHANNEL_ID = "sms2email_notifications"

fun createNotificationChannel(context: Context) {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel =
        NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "SMS2Email",
            NotificationManager.IMPORTANCE_LOW,
        )
    channel.description = "SMS forwarding status"
    notificationManager.createNotificationChannel(channel)
  }
}

fun notifyOutputSuccess(
    context: Context,
    outputLabel: String,
    eventBody: String,
) {
  notify(
      context = context,
      id = notificationId(outputLabel, success = true),
      title = "SMS sent with $outputLabel",
      text = eventBody.take(120),
      icon = android.R.drawable.ic_dialog_email,
  )
}

fun notifyOutputFailure(
    context: Context,
    outputLabel: String,
    throwable: Throwable,
) {
  notify(
      context = context,
      id = notificationId(outputLabel, success = false),
      title = "Failed to send with $outputLabel",
      text = throwable.message ?: throwable.javaClass.simpleName,
      icon = android.R.drawable.ic_dialog_alert,
  )
}

private fun notify(
    context: Context,
    id: Int,
    title: String,
    text: String,
    icon: Int,
) {
  runCatching {
    createNotificationChannel(context)
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.notify(
        id,
        NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(icon)
            .setAutoCancel(true)
            .build(),
    )
  }
}

private fun notificationId(label: String, success: Boolean): Int =
    (label.hashCode() and 0x3fffffff) + if (success) 1 else 2
