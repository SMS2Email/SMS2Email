package io.github.sms2email.sms2email;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
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

      // Detect which SIM received the SMS
      int simSlot = getSimSlotIndex(context, intent);

      mailSender.send(context, sender, message, simSlot);
    }
  }

  private int getSimSlotIndex(Context context, Intent intent) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
      try {
        // Try to get subscription ID from intent extras
        int subId = intent.getExtras().getInt("subscription", -1);
        if (subId == -1) {
          subId = intent.getExtras().getInt("android.telephony.extra.SUBSCRIPTION_INDEX", -1);
        }

        if (subId != -1) {
          SubscriptionManager subscriptionManager =
              (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
          if (subscriptionManager != null) {
            SubscriptionInfo info = subscriptionManager.getActiveSubscriptionInfo(subId);
            if (info != null) {
              return info.getSimSlotIndex();
            }
          }
        }
      } catch (Exception e) {
        // Fall through to return default
      }
    }
    return 0; // Default to SIM 1 if detection fails
  }
}
