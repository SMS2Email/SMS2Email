package io.github.sms2email.sms2email.preferences

import androidx.datastore.core.DataMigration
import io.github.sms2email.sms2email.Preferences

/** Starts Rust SDK users with no inherited Matrix settings or credentials. */
internal object ResetMatrixPreferencesMigration : DataMigration<Preferences> {
  override suspend fun shouldMigrate(currentData: Preferences): Boolean =
      !currentData.matrixRustMigrationComplete

  override suspend fun migrate(currentData: Preferences): Preferences =
      currentData
          .toBuilder()
          .clearMatrices()
          .addAllMatrices(initialPreferences.matricesList)
          .setMatrixRustMigrationComplete(true)
          .build()

  override suspend fun cleanUp() = Unit
}
