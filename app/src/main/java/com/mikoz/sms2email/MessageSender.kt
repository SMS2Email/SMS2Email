package io.github.sms2email.sms2email

import android.content.Context

/** Contract for forwarding incoming SMS messages to a configured destination provider. */
interface MessageSender {
  fun send(
      context: Context,
      sender: String,
      content: String,
  )
}
