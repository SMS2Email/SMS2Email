package io.github.sms2email.sms2email;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

public class NotificationHelper {
  public static final String CHANNEL_ID = "sms2email_notifications";

  public static void createNotificationChannel(Context context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationManager notificationManager =
          (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

      NotificationChannel channel =
          new NotificationChannel(
              CHANNEL_ID, "SMS2Email Notifications", NotificationManager.IMPORTANCE_LOW);
      channel.setDescription("Notifications for SMS2Email app events");

      notificationManager.createNotificationChannel(channel);
    }
  }
}
