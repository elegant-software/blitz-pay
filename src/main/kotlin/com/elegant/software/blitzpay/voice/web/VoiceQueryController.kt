package com.elegant.software.blitzpay.voice.web

import com.elegant.software.blitzpay.voice.api.VoiceGateway
import com.elegant.software.blitzpay.voice.config.VoiceProperties
import com.elegant.software.blitzpay.voice.internal.MissingAudioException
import com.elegant.software.blitzpay.voice.internal.MissingAuthorizationException
import com.elegant.software.blitzpay.voice.internal.PayloadTooLargeException
import com.elegant.software.blitzpay.voice.internal.UnsupportedAudioFormatException
import com.elegant.software.blitzpay.voice.internal.VoiceAudioSubmission
import com.elegant.software.blitzpay.voice.internal.VoiceException
import com.elegant.software.blitzpay.voice.internal.VoiceTranscriptionResponse
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.FilePart
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.Base64

@Tag(name = "Voice", description = "Voice query processing for authenticated mobile clients")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/voice", version = "1")
class VoiceQueryController(
    private val voiceGateway: VoiceGateway,
    private val properties: VoiceProperties,
) {
    private val objectMapper = jacksonObjectMapper()

    @Operation(summary = "Submit a voice recording and receive a transcription")
    @PostMapping(
        "/query",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    fun query(
        @RequestPart("audio", required = false) audio: FilePart?,
        @RequestHeader(name = "Authorization", required = false) authorization: String?,
    ): Mono<ResponseEntity<VoiceTranscriptionResponse>> =
        Mono.fromCallable {
            val callerSubject = extractCallerSubject(authorization)
            val audioPart = audio ?: throw MissingAudioException()
            val contentType = audioPart.headers().contentType?.toString() ?: throw MissingAudioException()

            if (contentType !in properties.acceptedContentTypes) {
                throw UnsupportedAudioFormatException(contentType)
            }

            val buffer = DataBufferUtils.join(audioPart.content()).block() ?: throw MissingAudioException()
            try {
                val bytes = ByteArray(buffer.readableByteCount())
                buffer.read(bytes)

                if (bytes.size.toLong() > properties.maxUploadBytes) {
                    throw PayloadTooLargeException(properties.maxUploadBytes)
                }

                val response = voiceGateway.process(
                    VoiceAudioSubmission(
                        bytes = bytes,
                        contentType = contentType,
                        filename = audioPart.filename(),
                        sizeBytes = bytes.size.toLong(),
                        callerSubject = callerSubject,
                    )
                )

                ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response)
            } finally {
                DataBufferUtils.release(buffer)
            }
        }.subscribeOn(Schedulers.boundedElastic())

    private fun extractCallerSubject(authorization: String?): String {
        val bearerToken = authorization
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.removePrefix("Bearer ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: throw MissingAuthorizationException()

        val parts = bearerToken.split('.')
        if (parts.size < 2) throw MissingAuthorizationException()

        val payload = runCatching {
            String(Base64.getUrlDecoder().decode(parts[1]))
        }.getOrElse { throw MissingAuthorizationException() }

        val subject = runCatching {
            objectMapper.readTree(payload).path("sub").asText(null)
        }.getOrNull()

        return subject?.takeIf { it.isNotBlank() }?.take(512) ?: throw MissingAuthorizationException()
    }

    @ExceptionHandler(VoiceException::class)
    fun handleVoiceException(ex: VoiceException): ResponseEntity<ProblemDetail> {
        val status = HttpStatus.valueOf(ex.statusCode)
        val problem = ProblemDetail.forStatusAndDetail(status, ex.message)
        problem.title = status.reasonPhrase
        problem.setProperty("reason", ex.reason)
        return ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problem)
    }
}
