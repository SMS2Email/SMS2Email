package io.github.sms2email.sms2email.preferences

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

fun activeSimCards(context: Context): List<AvailableSim> {
  if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) !=
      PackageManager.PERMISSION_GRANTED) {
    return emptyList()
  }

  return runCatching {
        context
            .getSystemService(SubscriptionManager::class.java)
            .activeSubscriptionInfoList
            .orEmpty()
            .map { info ->
              AvailableSim(
                  slotIndex = info.simSlotIndex,
                  subscriptionId = info.subscriptionId,
                  label =
                      info.displayName?.toString().orEmpty().ifBlank {
                        "SIM ${info.simSlotIndex + 1}"
                      },
              )
            }
      }
      .getOrDefault(emptyList())
      .sortedBy { it.slotIndex }
}
