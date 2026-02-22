
package com.elegant.software.blitzpay.payments.truelayer.inbound


import TlWebhookEnvelope

import com.elegant.software.blitzpay.payments.truelayer.support.JwksService
import com.elegant.software.blitzpay.payments.truelayer.support.TlWebhookProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.truelayer.signing.Verifier
import mu.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException


@RestController
@RequestMapping("/v1/webhooks/truelayer", version = "1")
class TlWebhookController(
    private val jwksService: JwksService,
    private val props: TlWebhookProperties,
    private val applicationEventPublisher: ApplicationEventPublisher
) {
    companion object {
        private val LOG = KotlinLogging.logger {}
    }
    private val mapper = jacksonObjectMapper()
    private val maxSkew = Duration.parse(props.maxSkew)

    @PostMapping
    fun receive(
        @RequestHeader headers: Map<String, String>,
        @RequestBody rawBody: String
    ): ResponseEntity<Any> {
        LOG.info("Webhook ${rawBody}")
        val signature = headers["tl-signature"]
            ?: headers["tl-signature"] // be generous with casing
            ?: run {
                LOG.warn("Unauthorized: Missing tl-signature header")
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            }

        // Optional: basic replay protection using X-TL-Webhook-Timestamp
        if (!timestampFresh(headers["x-tl-webhook-timestamp"])) {
            LOG.warn("Unauthorized: Timestamp is missing or not fresh enough")
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val path = "/v1/webhooks/truelayer" // must match exactly
        // Verify JKU is an allowed TrueLayer origin then fetch JWKS
        val jkuFromSig = try { Verifier.extractJku(signature) } catch (ex: Exception) {
            LOG.warn("Unauthorized: Failed to extract JKU from signature: ${'$'}{ex.message}")
            null
        }
        if (jkuFromSig == null || !jkuFromSig.equals(props.allowedJku, ignoreCase = true)) {
            LOG.warn("Unauthorized: JKU is null or does not match allowed JKU. Got: ${'$'}jkuFromSig, expected: ${'$'}{props.allowedJku}")
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val jwksJson = jwksService.fetchJwks().block() ?: run {
            LOG.warn("Unauthorized: Failed to fetch JWKS from service")
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val ok = try {
            Verifier.verifyWithJwks(jwksJson)
                .method("POST")
                .path(path)
                .headers(headers) // header names may be any casing
                .body(rawBody)
                .verify(signature)
            true
        } catch (ex: Exception) {
            LOG.warn("Unauthorized: Signature verification failed: ${'$'}{ex.message}")
            false
        }

        if (!ok) {
            LOG.warn("Unauthorized: Signature verification returned false")
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        // Parse body now that it’s verified
        val event: TlWebhookEnvelope = try {
            mapper.readValue(rawBody)

        } catch (_: Exception) {
            return ResponseEntity.badRequest().build()
        }
        applicationEventPublisher.publishEvent(event)


        return ResponseEntity.ok().build()
    }

    private fun timestampFresh(ts: String?): Boolean {
        if (ts == null) return false
        return try {
            val sent = Instant.parse(ts) // e.g. 2020-05-18T10:17:47Z
            val now = Instant.now()
            Duration.between(sent, now).abs() <= maxSkew
        } catch (_: DateTimeParseException) {
            false
        }
    }


}
