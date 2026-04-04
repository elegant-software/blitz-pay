package com.elegant.software.blitzpay.mobileobservability

import com.elegant.software.blitzpay.mobileobservability.api.AcceptedResponse
import com.elegant.software.blitzpay.mobileobservability.api.MobileLogsForwarder
import com.elegant.software.blitzpay.mobileobservability.api.MobileLogsRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Mobile Observability", description = "Mobile log ingestion and forwarding to OTLP")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/observability", version = "1")
class MobileLogsController(private val forwarder: MobileLogsForwarder) {

    @Operation(summary = "Ingest mobile logs", description = "Accepts batched log events from mobile clients and forwards them to the configured OTLP endpoint.")
    @PostMapping("/mobile-logs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun ingest(@Valid @RequestBody request: MobileLogsRequest): AcceptedResponse =
        AcceptedResponse(accepted = forwarder.forward(request))
}
