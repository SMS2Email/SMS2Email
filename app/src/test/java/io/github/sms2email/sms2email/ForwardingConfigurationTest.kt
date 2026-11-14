package io.github.sms2email.sms2email

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
  fun eachOutputCanSelectItsOwnInputs() {
    val emailInputs =
        listOf(
            SimInput("sim-1", slotIndex = 0, subscriptionId = 41, label = "SIM 1"),
            SimInput(
                "sim-2",
                slotIndex = 1,
                subscriptionId = 42,
                label = "SIM 2",
                enabled = false,
            ),
        )
    val matrixInputs =
        listOf(SimInput("sim-2", slotIndex = 1, subscriptionId = 42, label = "SIM 2"))

    assertTrue(acceptsInput(emailInputs, SmsEvent.create("+1", "one", slotIndex = 0)))
    assertFalse(acceptsInput(emailInputs, SmsEvent.create("+1", "two", slotIndex = 1)))
    assertTrue(acceptsInput(matrixInputs, SmsEvent.create("+1", "two", subscriptionId = 42)))
    assertFalse(acceptsInput(matrixInputs, SmsEvent.create("+1", "one", subscriptionId = 41)))
    assertFalse(acceptsInput(emailInputs, SmsEvent.create("+1", "unknown")))
  }

  @Test
  fun unconfiguredOutputAcceptsSms() {
    assertTrue(acceptsInput(emptyList(), SmsEvent.create("+1", "message")))
  }

  @Test
  fun sendersHaveSeparatePreferenceTypes() {
    assertTrue(EmailPreference("email", true).isConfigured().not())
    assertTrue(MatrixPreference("matrix", true).isConfigured().not())
  }
}
