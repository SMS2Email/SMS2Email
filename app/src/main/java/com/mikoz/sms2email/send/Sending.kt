package io.github.sms2email.sms2email.send

import android.content.Context
import io.github.sms2email.sms2email.notifyOutputFailure
import io.github.sms2email.sms2email.notifyOutputSuccess
import io.github.sms2email.sms2email.preferences.AppConfig
import io.github.sms2email.sms2email.preferences.PreferencesManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

suspend fun sendSms(
    context: Context,
    event: SmsEvent,
    bypassInputFilter: Boolean = false,
): Int {
  val appContext = context.applicationContext
  val config = PreferencesManager.getConfig(appContext)
  if (!bypassInputFilter && !acceptsInput(config, event)) return 0

  val jobs =
      buildList<suspend () -> Boolean> {
        config.emails
            .filter { it.enabled }
            .forEach { email ->
              add {
                sendOne(appContext, email.label.ifBlank { "Email" }, event) {
                  check(email.isConfigured()) { "Email sender is not configured" }
                  sendEmail(event, email)
                }
              }
            }
        config.matrix
            .filter { it.enabled }
            .forEach { matrix ->
              add {
                sendOne(appContext, matrix.label.ifBlank { "Matrix" }, event) {
                  check(matrix.isConfigured()) { "Matrix sender is not configured" }
                  sendMatrix(appContext, event, matrix)
                }
              }
            }
      }
  return coroutineScope { jobs.map { async { it() } }.awaitAll().count { it } }
}

private suspend fun sendOne(
    context: Context,
    label: String,
    event: SmsEvent,
    send: suspend () -> Unit,
): Boolean {
  val result = runCatching { send() }
  result
      .onSuccess { notifyOutputSuccess(context, label, event.body) }
      .onFailure { notifyOutputFailure(context, label, it) }
  return result.isSuccess
}

fun acceptsInput(config: AppConfig, event: SmsEvent): Boolean =
    !config.inputsSelected ||
        config.inputs.any { input ->
          input.enabled &&
              when {
                event.slotIndex != null -> event.slotIndex == input.slotIndex
                event.subscriptionId != null -> event.subscriptionId == input.subscriptionId
                else -> false
              }
        }
