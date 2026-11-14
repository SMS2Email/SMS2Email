package com.mikoz.sms2email

import io.github.sms2email.sms2email.preferences.AppConfig
import io.github.sms2email.sms2email.preferences.EmailPreference
import io.github.sms2email.sms2email.preferences.MatrixPreference
import io.github.sms2email.sms2email.preferences.SimInput
import io.github.sms2email.sms2email.send.SmsEvent
import io.github.sms2email.sms2email.send.acceptsInput
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForwardingConfigurationTest {
  @Test
  fun selectedInputsMatchBySlotOrSubscriptionAndIgnoreDisabledInputs() {
    val config =
        AppConfig(
            inputsSelected = true,
            inputs =
                listOf(
                    SimInput("sim-1", slotIndex = 0, subscriptionId = 41, label = "SIM 1"),
                    SimInput(
                        "sim-2",
                        slotIndex = 1,
                        subscriptionId = 42,
                        label = "SIM 2",
                        enabled = false,
                    ),
                ),
        )

    assertTrue(acceptsInput(config, SmsEvent.create("+1", "one", slotIndex = 0)))
    assertTrue(acceptsInput(config, SmsEvent.create("+1", "one", subscriptionId = 41)))
    assertFalse(acceptsInput(config, SmsEvent.create("+1", "two", slotIndex = 1)))
    assertFalse(acceptsInput(config, SmsEvent.create("+1", "unknown")))
  }

  @Test
  fun unconfiguredInputsAcceptSms() {
    assertTrue(acceptsInput(AppConfig(), SmsEvent.create("+1", "message")))
  }

  @Test
  fun sendersHaveSeparatePreferenceTypes() {
    assertTrue(EmailPreference("email", "Email", true).isConfigured().not())
    assertTrue(MatrixPreference("matrix", "Matrix", true).isConfigured().not())
  }
}
