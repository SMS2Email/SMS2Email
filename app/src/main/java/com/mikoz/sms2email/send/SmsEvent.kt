package io.github.sms2email.sms2email.send

data class SmsEvent(
    val id: String,
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val subscriptionId: Int? = null,
    val slotIndex: Int? = null,
) {
  companion object {
    fun create(
        sender: String,
        body: String,
        receivedAt: Long = System.currentTimeMillis(),
        subscriptionId: Int? = null,
        slotIndex: Int? = null,
    ) =
        SmsEvent(
            id =
                "$subscriptionId|$slotIndex|$receivedAt|$sender|$body"
                    .hashCode()
                    .toUInt()
                    .toString(16),
            sender = sender,
            body = body,
            receivedAt = receivedAt,
            subscriptionId = subscriptionId,
            slotIndex = slotIndex,
        )
  }
}
