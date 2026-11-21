package io.github.sms2email.sms2email.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.matrix.rustcomponents.sdk.SessionVerificationController
import org.matrix.rustcomponents.sdk.SessionVerificationRequestDetails

sealed interface MatrixVerificationPrompt {
  val controller: SessionVerificationController

  data class Incoming(
      override val controller: SessionVerificationController,
      val details: SessionVerificationRequestDetails,
  ) : MatrixVerificationPrompt

  data class Waiting(
      override val controller: SessionVerificationController,
      val message: String,
  ) : MatrixVerificationPrompt

  data class Emojis(
      override val controller: SessionVerificationController,
      val emojis: List<MatrixSasEmoji>,
  ) : MatrixVerificationPrompt

  data class Decimals(
      override val controller: SessionVerificationController,
      val values: List<Int>,
  ) : MatrixVerificationPrompt

  data class Finished(override val controller: SessionVerificationController) :
      MatrixVerificationPrompt

  data class Failed(
      override val controller: SessionVerificationController,
      val message: String,
  ) : MatrixVerificationPrompt
}

data class MatrixSasEmoji(val symbol: String, val description: String)

@Composable
fun MatrixVerificationDialog(
    prompt: MatrixVerificationPrompt,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onConfirm: () -> Unit,
    onMismatch: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
) {
  val incoming = prompt as? MatrixVerificationPrompt.Incoming
  val emojiSas = prompt as? MatrixVerificationPrompt.Emojis
  val decimalSas = prompt as? MatrixVerificationPrompt.Decimals
  val finished = prompt is MatrixVerificationPrompt.Finished
  val failed = prompt as? MatrixVerificationPrompt.Failed

  AlertDialog(
      onDismissRequest = { if (finished || failed != null) onDone() else onCancel() },
      title = {
        Text(
            when {
              incoming != null -> "Verify this device"
              emojiSas != null || decimalSas != null -> "Compare security codes"
              finished -> "Device verified"
              failed != null -> "Verification ended"
              else -> "Waiting for verification"
            },
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          when {
            incoming != null -> {
              val details = incoming.details
              Text(
                  "${details.senderProfile.userId} wants to verify " +
                      (details.deviceDisplayName ?: details.deviceId) +
                      ". Accept only if you started this on your other device.")
            }
            emojiSas != null -> {
              Text("Compare these seven emoji, in order, with the other device.")
              emojiSas.emojis.forEachIndexed { index, emoji ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  Text(emoji.symbol, fontSize = 28.sp)
                  Text(
                      "${index + 1}. ${emoji.description}", modifier = Modifier.padding(top = 6.dp))
                }
              }
            }
            decimalSas != null -> {
              Text("Compare this code with the other device:")
              Text(decimalSas.values.joinToString(" - "), fontSize = 24.sp)
            }
            finished -> Text("The Matrix device verification completed successfully.")
            failed != null -> Text(failed.message)
            prompt is MatrixVerificationPrompt.Waiting -> Text(prompt.message)
          }
        }
      },
      confirmButton = {
        when {
          incoming != null -> Button(onClick = onAccept) { Text("Accept") }
          emojiSas != null || decimalSas != null ->
              Button(onClick = onConfirm) { Text("They match") }
          finished || failed != null -> Button(onClick = onDone) { Text("Done") }
          else -> TextButton(onClick = onCancel) { Text("Cancel") }
        }
      },
      dismissButton = {
        when {
          incoming != null -> TextButton(onClick = onReject) { Text("Reject") }
          emojiSas != null || decimalSas != null ->
              TextButton(onClick = onMismatch) { Text("They differ") }
        }
      },
  )
}
