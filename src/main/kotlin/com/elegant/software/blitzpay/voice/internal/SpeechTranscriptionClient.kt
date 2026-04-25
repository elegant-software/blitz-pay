package com.elegant.software.blitzpay.voice.internal

interface SpeechTranscriptionClient {
    fun transcribe(submission: VoiceAudioSubmission): VoiceTranscription
}
