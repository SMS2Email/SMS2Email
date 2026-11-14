package io.github.sms2email.sms2email

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import android.telephony.SubscriptionManager
import io.github.sms2email.sms2email.send.SmsEvent
import io.github.sms2email.sms2email.send.sendSms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

    val event = parseSmsIntent(context, intent) ?: return
    val pendingResult = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
      try {
        sendSms(context.applicationContext, event)
      } catch (_: Throwable) {
        notifySmsForwarding(context, event, 0, 0)
      } finally {
        pendingResult.finish()
      }
    }
  }
}

fun parseSmsIntent(
    context: Context,
    intent: Intent,
): SmsEvent? {
  val messages: Array<SmsMessage> =
      runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }.getOrNull() ?: return null
  val first = messages.firstOrNull() ?: return null
  val sender =
      (first.displayOriginatingAddress ?: first.originatingAddress)?.takeIf { it.isNotBlank() }
          ?: return null
  val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
  val subscriptionId = subscriptionId(intent)
  val slotIndex = slotIndex(context, intent, subscriptionId)
  return SmsEvent.create(
      sender = sender,
      body = body,
      receivedAt = first.timestampMillis,
      subscriptionId = subscriptionId,
      slotIndex = slotIndex,
  )
}

private fun subscriptionId(intent: Intent): Int? =
    intent.getIntExtra(SUBSCRIPTION_INDEX_EXTRA, INVALID_ID).takeIf { it != INVALID_ID }

private fun slotIndex(
    context: Context,
    intent: Intent,
    subscriptionId: Int?,
): Int? {
  val fromIntent = intent.getIntExtra(SLOT_INDEX_EXTRA, INVALID_ID).takeIf { it != INVALID_ID }
  if (fromIntent != null) return fromIntent

  val fromSubscription =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            subscriptionId?.let { SubscriptionManager.getSlotIndex(it) }
          } else {
            null
          }
          ?.takeIf { it != INVALID_ID }
  if (fromSubscription != null) return fromSubscription

  return io.github.sms2email.sms2email.preferences
      .activeSimCards(context)
      .singleOrNull { it.subscriptionId == subscriptionId }
      ?.slotIndex
}

private const val SUBSCRIPTION_INDEX_EXTRA = "android.telephony.extra.SUBSCRIPTION_INDEX"
private const val SLOT_INDEX_EXTRA = "android.telephony.extra.SLOT_INDEX"
private const val INVALID_ID = -1
