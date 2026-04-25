# Quickstart: Voice Query and Spoken Response

## What Is Being Built

Add a new `voice` module that exposes `POST /v1/voice/query`. The endpoint accepts an authenticated multipart audio upload, transcribes it, enriches the prompt with BlitzPay domain context, generates a spoken answer, and returns `audio/mpeg` in the same request.

## Source layout

```text
src/main/kotlin/com/elegant/software/blitzpay/voice/
├── api/
│   ├── package-info.kt
│   └── VoiceGateway.kt
├── config/
│   ├── VoiceProperties.kt
│   └── VoiceConfiguration.kt
├── internal/
│   ├── VoiceQueryService.kt
│   ├── VoicePromptBuilder.kt
│   ├── SpeechTranscriptionClient.kt
│   ├── VoiceReasoningClient.kt
│   ├── SpeechSynthesisClient.kt
│   └── VoiceExceptions.kt
└── web/
    └── VoiceQueryController.kt

src/main/kotlin/com/elegant/software/blitzpay/payments/push/api/
├── PaymentVoiceContextGateway.kt
└── RecentPaymentSummary.kt

src/main/resources/db/changelog/
└── 20260425-001-push-payment-voice-context.sql
```

## Request/response flow

```text
Mobile app
  -> POST /v1/voice/query (Bearer token + multipart audio)
  -> VoiceQueryController
  -> validate auth, mime type, and upload size
  -> VoiceQueryService
     -> SpeechTranscriptionClient
     -> PaymentVoiceContextGateway
     -> VoiceReasoningClient
     -> SpeechSynthesisClient
  -> 200 OK audio/mpeg
```

## Configuration

Add `blitzpay.voice.*` properties in `application.yml` and source secrets from environment:

```yaml
blitzpay:
  voice:
    enabled: ${VOICE_ENABLED:true}
    max-duration-seconds: ${VOICE_MAX_DURATION_SECONDS:60}
    max-upload-bytes: ${VOICE_MAX_UPLOAD_BYTES:26214400}
    accepted-content-types:
      - audio/mpeg
      - audio/mp4
      - audio/webm
      - audio/wav
      - audio/ogg
    output-content-type: audio/mpeg
```

## Testing checklist

1. Add `src/test/kotlin/com/elegant/software/blitzpay/voice/` service and controller tests.
2. Add `src/contractTest/kotlin/com/elegant/software/blitzpay/voice/VoiceQueryControllerContractTest.kt`.
3. Extend modulith verification only if a new named interface or module package is introduced.
4. Run `./gradlew check` after implementation.

## Example request

```bash
curl -X POST http://localhost:8080/v1/voice/query \
  -H "Authorization: Bearer <jwt>" \
  -F "audio=@question.m4a;type=audio/mp4"
```

On success, the response body is binary MP3 audio that the mobile app can play directly.

## Validation Notes

- Targeted unit tests passed:
  - `com.elegant.software.blitzpay.voice.web.VoiceQueryControllerTest`
  - `com.elegant.software.blitzpay.voice.internal.VoiceQueryServiceTest`
  - `com.elegant.software.blitzpay.voice.internal.VoicePromptBuilderTest`
  - `com.elegant.software.blitzpay.payments.push.PaymentVoiceContextServiceTest`
- Targeted contract test passed:
  - `com.elegant.software.blitzpay.voice.VoiceQueryControllerContractTest`
- Full verification passed:
  - `./gradlew check`
