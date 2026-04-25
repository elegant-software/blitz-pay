package com.elegant.software.blitzpay.voice.api

import com.elegant.software.blitzpay.voice.internal.VoiceAudioSubmission
import com.elegant.software.blitzpay.voice.internal.VoiceTranscriptionResponse

interface VoiceGateway {
    fun process(submission: VoiceAudioSubmission): VoiceTranscriptionResponse
}
