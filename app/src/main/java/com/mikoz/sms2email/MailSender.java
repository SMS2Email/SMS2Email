package com.mikoz.sms2email;

import java.io.FileReader;
import java.util.Properties;

import java.io.IOException;
import java.io.FileNotFoundException;
import javax.mail.MessagingException;

import javax.mail.Session;
import javax.mail.Message;
import javax.mail.Transport;
import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.InternetAddress;

public class MailSender {
    public static void main(String[] args) throws IOException, FileNotFoundException{
        Properties prop = new Properties();
        prop.load(new FileReader("src/setting/mail.properties"));

        final String username = prop.getProperty("mailaddress");
        final String password = prop.getProperty("password");

        Session session = Session.getInstance(prop, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(username));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(prop.getProperty(username)));
            message.setSubject("JavaMail Test");
            message.setText("This is a test email sent from JavaMail.");

            Transport.send(message);
            System.out.println("Email sent successfully.");
        } catch (MessagingException e) {
            System.err.println("Email sent unsuccessfully ： " + e );
        }
    }
}
