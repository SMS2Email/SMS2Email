package io.github.sms2email.sms2email

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

const val NOTIFICATION_CHANNEL_ID = "sms2email_notifications"

fun createNotificationChannel(context: Context) {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val channel =
        NotificationChannel(
            NOTIFICATION_CHANNEL_ID, "SMS2Email Notifications", NotificationManager.IMPORTANCE_LOW)
    channel.description = "Notifications for SMS2Email app events"

    notificationManager.createNotificationChannel(channel)
  }
}
