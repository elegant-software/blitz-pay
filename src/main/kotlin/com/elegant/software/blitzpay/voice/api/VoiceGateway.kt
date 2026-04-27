package com.elegant.software.blitzpay.voice.api

import com.elegant.software.blitzpay.voice.internal.VoiceAudioSubmission

interface VoiceGateway {
    fun process(submission: VoiceAudioSubmission): AssistantResponse
}
