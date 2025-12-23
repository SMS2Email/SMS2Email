package com.mikoz.sms2email;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;

import java.util.Objects;

public class SmsReceiver extends BroadcastReceiver {
    protected MailSender mailSender;
    public SmsReceiver(MailSender mailSender) {
        super();
        this.mailSender = mailSender;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Objects.equals(intent.getAction(), Telephony.Sms.Intents.SMS_RECEIVED_ACTION)) {
            SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
            String sender = messages[0].getOriginatingAddress();
            String message = sb.toString();
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            new NotificationCompat.Builder(context.getApplicationContext())
                            .setContentTitle(sender)
                            .setContentText(message)
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT).notify();
            mailSender.send(context, sender, message);
        }
    }
}
