package com.elegant.software.blitzpay.voice.api

import com.elegant.software.blitzpay.voice.service.VoiceAudioSubmission

interface VoiceGateway {
    fun process(submission: VoiceAudioSubmission): AssistantResponse
}
