package com.elegant.software.blitzpay.voice.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "blitzpay.voice")
data class VoiceProperties(
    val enabled: Boolean = true,
    val maxDurationSeconds: Long = 60,
    val maxUploadBytes: Long = 26_214_400,
    val acceptedContentTypes: List<String> = listOf(
        "audio/mpeg",
        "audio/mp4",
        "audio/webm",
        "audio/wav",
        "audio/ogg",
    ),
    val requestTimeoutMs: Long = 10_000,
    val whisper: Whisper = Whisper(),
    val ollama: Ollama = Ollama(),
) {
    data class Whisper(
        val apiKey: String = "",
        val baseUrl: String = "https://api.openai.com/v1",
        val model: String = "whisper-1",
    )

    data class Ollama(
        val timeoutMs: Long = 4_000,
    )
}
