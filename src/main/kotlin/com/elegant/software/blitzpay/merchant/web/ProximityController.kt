package com.elegant.software.blitzpay.merchant.web

import com.elegant.software.blitzpay.merchant.api.ProximityEventRequest
import com.elegant.software.blitzpay.merchant.api.ProximityResponse
import com.elegant.software.blitzpay.merchant.application.ProximityService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebInputException
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.Base64

@Tag(name = "Proximity", description = "Mobile geofence enter and exit event recording")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/proximity", version = "1")
class ProximityController(
    private val proximityService: ProximityService,
) {
    private val log = LoggerFactory.getLogger(ProximityController::class.java)
    private val objectMapper = jacksonObjectMapper()

    @Operation(summary = "Record a geofence proximity event and return merchant context when applicable")
    @PostMapping
    fun record(
        @RequestBody request: ProximityEventRequest,
        @RequestHeader(name = "Authorization", required = false) authorization: String?,
    ): Mono<ResponseEntity<ProximityResponse>> =
        Mono.fromCallable {
            val userSubject = extractUserSubject(authorization)

            log.info(
                "Proximity event received: regionId={} event={} deviceId={} authenticated={}",
                request.regionId,
                request.event,
                request.deviceId,
                userSubject != null,
            )

            ResponseEntity.ok(proximityService.record(request, userSubject))
        }
            .map {
                log.info(
                    "Proximity event response: recorded={} action={} merchantId={}",
                    it.body?.recorded,
                    it.body?.action,
                    it.body?.merchant?.merchantId,
                )
                it
            }
            .subscribeOn(Schedulers.boundedElastic())

    private fun extractUserSubject(authorization: String?): String? {
        val bearerToken = authorization
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.removePrefix("Bearer ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val subject = runCatching {
            val parts = bearerToken.split('.')
            if (parts.size < 2) return@runCatching null
            val payload = String(Base64.getUrlDecoder().decode(parts[1]))
            objectMapper.readTree(payload).path("sub").asText(null)
        }.getOrNull()

        if (subject.isNullOrBlank()) {
            log.info("Proximity event bearer token did not contain a usable subject; skipping user subject persistence")
            return null
        }

        return subject.take(512)
    }

    @ExceptionHandler(ServerWebInputException::class)
    fun handleInvalidRequest(ex: ServerWebInputException): ResponseEntity<ProblemDetail> {
        log.warn("Invalid proximity request: reason={}", ex.reason)
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.reason ?: "Invalid request body")
        problem.title = "Unprocessable Entity"
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem)
    }
}
