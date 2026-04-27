package com.elegant.software.blitzpay.voice.internal

import com.elegant.software.blitzpay.merchant.api.MerchantProductCatalogGateway
import com.elegant.software.blitzpay.voice.api.AssistantResponse
import com.elegant.software.blitzpay.voice.api.VoiceGateway
import com.elegant.software.blitzpay.voice.config.VoiceProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class VoiceQueryService(
    private val transcriptionClient: SpeechTranscriptionClient,
    private val properties: VoiceProperties,
    private val productCatalogGateway: MerchantProductCatalogGateway,
    private val productIntentExtractor: ProductIntentExtractor,
    private val productCatalogSearch: ProductCatalogSearch,
) : VoiceGateway {
    private val log = LoggerFactory.getLogger(VoiceQueryService::class.java)

    override fun process(submission: VoiceAudioSubmission): AssistantResponse {
        val transcription = transcriptionClient.transcribe(submission)

        transcription.durationSeconds?.let { duration ->
            if (duration.toDouble() < 1.0) throw AudioTooShortException()
            if (duration.toLong() > properties.maxDurationSeconds) throw AudioTooLongException(properties.maxDurationSeconds)
        }

        log.info(
            "voice transcription completed subject={} transcriptLength={}",
            submission.callerSubject,
            transcription.text.length,
        )

        val merchantId = submission.merchantId
        val branchId = submission.branchId

        if (merchantId == null || branchId == null) {
            return AssistantResponse.Transcript(
                transcript = transcription.text,
                language = transcription.language,
            )
        }

        val catalog = productCatalogGateway.findActiveProducts(merchantId, branchId)
        val intent = productIntentExtractor.extract(transcription.text, catalog)

        log.info(
            "product intent extracted subject={} matches={} quantity={}",
            submission.callerSubject,
            intent.matchedProductIds.size,
            intent.requestedQuantity,
        )

        return productCatalogSearch.search(intent, catalog)
    }
}
