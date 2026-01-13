package io.github.sms2email.sms2email;

import android.app.NotificationManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
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

    // Configure transport encryption.
    switch (config.getEncryptionMode()) {
      case SMTP_ENCRYPTION_MODE_NONE:
        prop.setProperty("mail.smtp.starttls.enable", "false");
        prop.setProperty("mail.smtp.ssl.enable", "false"); // enabled by default
        break;
      case SMTP_ENCRYPTION_MODE_SMTPS:
        prop.setProperty("mail.smtp.starttls.enable", "false");
        prop.setProperty("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        prop.setProperty("mail.smtp.socketFactory.fallback", "false");
        prop.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        break;
      case SMTP_ENCRYPTION_MODE_STARTTLS:
      default:
        prop.setProperty("mail.smtp.starttls.enable", "true");
        prop.setProperty("mail.smtp.starttls.required", "true");
        prop.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        break;
    }

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
      message.setFrom(InternetAddress.parse(subject + " <" + config.getToEmail() + ">")[0]);
      message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(config.getToEmail()));
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

      // Show toast notification on failure
      new Handler(Looper.getMainLooper())
          .post(
              () ->
                  Toast.makeText(
                          context, "Failed to send email\n" + e.getMessage(), Toast.LENGTH_LONG)
                      .show());

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
