package io.github.sms2email.sms2email.send

import android.content.Context
import io.github.sms2email.sms2email.notifySmsForwarding
import io.github.sms2email.sms2email.preferences.SimInput
import io.github.sms2email.sms2email.preferences.getConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

suspend fun sendSms(
    context: Context,
    event: SmsEvent,
    bypassInputFilter: Boolean = false,
): Int {
  val appContext = context.applicationContext
  val config = getConfig(appContext)
  val emails =
      config.emails.filter {
        it.enabled && it.isConfigured() && (bypassInputFilter || acceptsInput(it.inputs, event))
      }
  val matrices =
      config.matrix.filter {
        it.enabled && it.isConfigured() && (bypassInputFilter || acceptsInput(it.inputs, event))
      }
  val totalOutputs = emails.size + matrices.size

  val jobs =
      buildList<suspend () -> Boolean> {
        emails.forEach { email -> add { runCatching { sendEmail(event, email) }.isSuccess } }
        matrices.forEach { matrix ->
          add { runCatching { sendMatrix(appContext, event, matrix) }.isSuccess }
        }
      }
  val results = coroutineScope { jobs.map { async { it() } }.awaitAll() }
  val successful = results.count { it }
  notifySmsForwarding(appContext, event, successful, totalOutputs)
  return successful
}

fun acceptsInput(inputs: List<SimInput>, event: SmsEvent): Boolean =
    inputs.isEmpty() ||
        inputs.any { input ->
          input.enabled &&
              when {
                event.slotIndex != null -> event.slotIndex == input.slotIndex
                event.subscriptionId != null -> event.subscriptionId == input.subscriptionId
                else -> false
              }
        }
