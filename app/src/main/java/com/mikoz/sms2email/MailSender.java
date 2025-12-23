package com.mikoz.sms2email;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class MailSender {
  public void send(Context context, String subject, String content)
      throws IOException, FileNotFoundException {
    final SharedPreferences pref = context.getSharedPreferences("", Context.MODE_PRIVATE);
    final Properties prop = new Properties();
    prop.put("mail.smtp.host", pref.getString("smtp.host", "smtp.gmail.com"));
    prop.put("mail.smtp.port", pref.getInt("smtp.port", 587));
    prop.put("mail.smtp.auth", "true");
    prop.put("mail.smtp.starttls.enable", "true");
    Session session =
        Session.getInstance(
            prop,
            new Authenticator() {
              protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(
                    pref.getString("smtp.user", ""), pref.getString("smtp.password", ""));
              }
            });

    try {
      Message message = new MimeMessage(session);
      message.setFrom(new InternetAddress(pref.getString("from", "")));
      message.setRecipients(
          Message.RecipientType.TO, new InternetAddress(pref.getString("to", "")));
      message.setSubject(subject, "UTF-8");
      message.setText(content, "UTF-8");
      Transport.send(message);
    } catch (MessagingException e) {

    }
  }
}
