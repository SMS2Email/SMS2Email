package com.mikoz.sms2email;

import android.app.NotificationManager;
import android.content.Context;
import androidx.core.app.NotificationCompat;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Properties;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class MailSender {
  public void send(Context context, String subject, String content) {
    new Thread(() -> sendSync(context, subject, content)).start();
  }

  private void sendSync(Context context, String subject, String content) {
    final SmtpConfig config = PreferencesManager.getConfigBlocking(context);
    final int smtpPort = config.getSmtpPort();
    final Properties prop = new Properties();
    prop.put("mail.smtp.host", config.getSmtpHost());
    prop.put("mail.smtp.port", smtpPort);
    prop.put("mail.smtp.auth", "true");
    prop.put("mail.smtp.starttls.enable", "true");

    try {
      Session session =
          Session.getInstance(
              prop,
              new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                  return new PasswordAuthentication(config.getSmtpUser(), config.getSmtpPassword());
                }
              });
      Message message = new MimeMessage(session);
      message.setFrom(new InternetAddress(config.getFromEmail()));
      message.setRecipients(
          Message.RecipientType.TO,
          InternetAddress.parse(subject + " <" + config.getToEmail() + ">"));
      message.setSubject("SMS from " + subject);
      message.setText(content);
      Transport.send(message);

      NotificationHelper.createNotificationChannel(context);

      NotificationManager notificationManager =
          (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

      NotificationCompat.Builder builder =
          new NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
              .setContentTitle("Transferred SMS from " + subject)
              .setContentText(content)
              .setSmallIcon(android.R.drawable.ic_dialog_email);

      notificationManager.notify(2, builder.build());
    } catch (Exception e) {
      // Log the full stack trace
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      e.printStackTrace(pw);
      String stackTrace = sw.toString();

      NotificationHelper.createNotificationChannel(context);

      NotificationManager notificationManager =
          (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

      NotificationCompat.Builder builder =
          new NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
              .setContentTitle("Failed to send email")
              .setContentText(e.getMessage())
              .setStyle(
                  new NotificationCompat.BigTextStyle()
                      .bigText(e.getMessage() + "\n\n" + stackTrace))
              .setSmallIcon(android.R.drawable.ic_dialog_email);

      notificationManager.notify(1, builder.build());
    }
  }
}
