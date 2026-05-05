package com.elegant.software.blitzpay.voice.service

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

        val catalog = when {
            merchantId != null && branchId != null -> {
                log.info(
                    "voice query using explicit merchant context subject={} merchantId={} branchId={}",
                    submission.callerSubject,
                    merchantId,
                    branchId,
                )
                productCatalogGateway.findActiveProducts(merchantId, branchId)
            }
            else -> {
                val contextualCatalog = productCatalogGateway.findActiveProductsBySubject(submission.callerSubject)
                if (contextualCatalog.isNotEmpty()) {
                    log.info(
                        "voice query resolved catalog from subject proximity subject={} productCount={}",
                        submission.callerSubject,
                        contextualCatalog.size,
                    )
                    contextualCatalog
                } else {
                    val globalCatalog = productCatalogGateway.searchActiveProducts(transcription.text)
                    log.info(
                        "voice query falling back to global product search subject={} productCount={} transcriptLength={}",
                        submission.callerSubject,
                        globalCatalog.size,
                        transcription.text.length,
                    )
                    globalCatalog
                }
            }
        }

        if (catalog.isEmpty()) {
            log.info(
                "voice query no product catalog candidates subject={} returning=TRANSCRIPT",
                submission.callerSubject,
            )
            return AssistantResponse.Transcript(
                transcript = transcription.text,
                language = transcription.language,
            )
        }

        log.info(
            "voice product catalog loaded subject={} merchantId={} branchId={} productCount={} sampleProductIds={}",
            submission.callerSubject,
            merchantId,
            branchId,
            catalog.size,
            catalog.take(5).map { "${it.productId}:${it.name.take(32)}" }
        )
        val intent = productIntentExtractor.extract(transcription.text, catalog)

        log.info(
            "product intent extracted subject={} matches={} quantity={} matchedProductIds={}",
            submission.callerSubject,
            intent.matchedProductIds.size,
            intent.requestedQuantity,
            intent.matchedProductIds.take(5),
        )

        return productCatalogSearch.search(intent, catalog).also { response ->
            log.info(
                "voice product response built subject={} responseType={}",
                submission.callerSubject,
                response.javaClass.simpleName,
            )
        }
    }
}
