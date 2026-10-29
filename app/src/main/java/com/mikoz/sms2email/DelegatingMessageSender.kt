package io.github.sms2email.sms2email

import android.content.Context

/**
 * [MessageSender] implementation that delegates to the currently active provider ([MailSender] or
 * [HttpSender]) based on user preferences.
 */
class DelegatingMessageSender
@JvmOverloads
constructor(
    private val emailSender: MessageSender = MailSender(),
    private val httpSender: MessageSender = HttpSender(),
    private val providerResolver: (Context) -> ForwardingProvider = { context ->
      PreferencesManager.getConfigBlocking(context).provider
    },
) : MessageSender {

  override fun send(
      context: Context,
      sender: String,
      content: String,
  ) {
    val activeSender =
        when (providerResolver(context)) {
          ForwardingProvider.FORWARDING_PROVIDER_HTTP -> httpSender
          ForwardingProvider.FORWARDING_PROVIDER_EMAIL,
          ForwardingProvider.UNRECOGNIZED -> emailSender
        }
    activeSender.send(context, sender, content)
  }
}
