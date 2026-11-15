package io.github.sms2email.sms2email.preferences

import io.github.sms2email.sms2email.InputPreference as InputProto
import io.github.sms2email.sms2email.LegacyPreferences
import io.github.sms2email.sms2email.Preferences

/** Migrates the pre-per-output preferences.pb schema. */
internal object LegacyPreferencesMigration : androidx.datastore.core.DataMigration<Preferences> {
  override suspend fun shouldMigrate(currentData: Preferences): Boolean =
      parseLegacyPreferences(currentData)?.let { it.inputsSelected && it.inputsCount > 0 } == true

  override suspend fun migrate(currentData: Preferences): Preferences {
    val legacy = parseLegacyPreferences(currentData) ?: return currentData
    if (!legacy.inputsSelected || legacy.inputsCount == 0) return currentData

    val inputs = legacy.inputsList.map(::legacyInputPreference)
    val emails =
        currentData.emailsList.map { email ->
          if (email.inputsCount == 0) email.toBuilder().addAllInputs(inputs).build() else email
        }
    val matrices =
        currentData.matricesList.map { matrix ->
          if (matrix.inputsCount == 0) matrix.toBuilder().addAllInputs(inputs).build() else matrix
        }

    // Rebuild without the old top-level fields so this migration is one-shot.
    return Preferences.newBuilder().addAllEmails(emails).addAllMatrices(matrices).build()
  }

  override suspend fun cleanUp() = Unit
}

private fun parseLegacyPreferences(currentData: Preferences): LegacyPreferences? =
    runCatching { LegacyPreferences.parseFrom(currentData.toByteArray()) }.getOrNull()

private fun legacyInputPreference(input: io.github.sms2email.sms2email.LegacyInputPreference) =
    InputProto.newBuilder()
        .setId(input.id)
        .setSlotIndex(input.slotIndex)
        .setSubscriptionId(input.subscriptionId)
        .setLabel(input.label)
        .setEnabled(input.enabled)
        .build()
