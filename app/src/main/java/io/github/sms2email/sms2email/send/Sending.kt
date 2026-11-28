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
  val configuredOutputs =
      config.emails.count { it.isConfigured() } + config.matrix.count { it.isConfigured() }

  val jobs =
      buildList<suspend () -> Boolean> {
        config.emails
            .filter { email ->
              email.enabled && (bypassInputFilter || acceptsInput(email.inputs, event))
            }
            .forEach { email ->
              add {
                runCatching {
                      check(email.isConfigured()) { "Email sender is not configured" }
                      sendEmail(appContext, event, email)
                    }
                    .isSuccess
              }
            }
        config.matrix
            .filter { matrix ->
              matrix.enabled && (bypassInputFilter || acceptsInput(matrix.inputs, event))
            }
            .forEach { matrix ->
              add {
                runCatching {
                      check(matrix.isConfigured()) { "Matrix sender is not configured" }
                      sendMatrix(appContext, event, matrix)
                    }
                    .isSuccess
              }
            }
      }
  val results = coroutineScope { jobs.map { async { it() } }.awaitAll() }
  val successful = results.count { it }
  notifySmsForwarding(appContext, event, successful, configuredOutputs)
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
