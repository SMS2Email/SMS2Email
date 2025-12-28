package io.github.sms2email.sms2email;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import java.util.Objects;

public class SmsReceiver extends BroadcastReceiver {
  protected MailSender mailSender = new MailSender();

  @Override
  public void onReceive(Context context, Intent intent) {
    if (Objects.equals(intent.getAction(), Telephony.Sms.Intents.SMS_RECEIVED_ACTION)) {
      SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
      String sender = messages[0].getOriginatingAddress();
      StringBuilder bodyText = new StringBuilder();
      for (SmsMessage message : messages) {
        bodyText.append(message.getMessageBody());
      }
      String message = bodyText.toString();

      mailSender.send(context, sender, message);
    }
  }
}
