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

fun notifySmsForwarding(
    context: Context,
    event: io.github.sms2email.sms2email.send.SmsEvent,
    successfulOutputs: Int,
    totalOutputs: Int,
) {
  val succeeded = successfulOutputs > 0
  val noOutputs = totalOutputs == 0
  notify(
      context,
      notificationId(event.id),
      when {
        noOutputs -> "SMS received"
        succeeded -> "SMS from ${event.sender} transferred ($successfulOutputs/$totalOutputs)"
        else -> "SMS forwarding failed"
      },
      when {
        noOutputs -> "No enabled configured output accepts this SMS."
        succeeded -> event.body.take(120)
        else -> "$successfulOutputs of $totalOutputs active output(s) succeeded."
      },
      when {
        noOutputs -> android.R.drawable.ic_dialog_info
        succeeded -> android.R.drawable.ic_dialog_email
        else -> android.R.drawable.ic_dialog_alert
      },
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

private fun notificationId(eventId: String): Int = eventId.hashCode() and 0x3fffffff
