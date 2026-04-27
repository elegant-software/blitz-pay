package com.elegant.software.blitzpay.voice.internal

import java.math.BigDecimal
import java.util.UUID

data class VoiceAudioSubmission(
    val bytes: ByteArray,
    val contentType: String,
    val filename: String? = null,
    val sizeBytes: Long = bytes.size.toLong(),
    val callerSubject: String,
    val merchantId: UUID? = null,
    val branchId: UUID? = null,
)

data class VoiceTranscription(
    val text: String,
    val language: String? = null,
    val durationSeconds: BigDecimal? = null,
)

data class ProductIntent(
    val matchedProductIds: List<UUID>,
    val requestedQuantity: Int?,
)
