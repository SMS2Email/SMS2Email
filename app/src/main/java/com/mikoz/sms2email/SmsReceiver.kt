package io.github.sms2email.sms2email

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage

class SmsReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
      val messages: Array<SmsMessage> = Telephony.Sms.Intents.getMessagesFromIntent(intent)
      val sender = messages[0].originatingAddress!!
      val message = buildString {
        for (smsMessage in messages) {
          append(smsMessage.messageBody)
        }
      }

      sendMail(context, sender, message)
    }
  }
}
