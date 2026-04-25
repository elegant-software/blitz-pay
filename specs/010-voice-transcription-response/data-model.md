# Data Model: Voice Query and Spoken Response

## Persistence boundary

Voice interactions are synchronous and ephemeral. No `voice_*` table is introduced for v1. The only durable model change is an enrichment of payment lifecycle data owned by `payments.push` so voice prompts can reference recent user payment activity.

## New in-flight models in `voice.internal`

### `VoiceAudioSubmission`

Validated multipart upload passed from the controller to the orchestration service.

| Field | Type | Notes |
|---|---|---|
| `bytes` | `ByteArray` | Raw audio payload held in memory for a single request |
| `contentType` | `String` | Client-declared MIME type after allow-list validation |
| `filename` | `String?` | Optional original filename for diagnostics |
| `sizeBytes` | `Long` | Checked against configured upload limit |
| `callerSubject` | `String` | Required authenticated subject extracted from bearer token |

### `VoiceTranscription`

| Field | Type | Notes |
|---|---|---|
| `text` | `String` | Non-blank transcript used for prompt building |
| `language` | `String?` | Optional detected language code |
| `durationSeconds` | `BigDecimal?` | Optional provider-reported duration for validation/logging |

### `VoicePromptContext`

| Field | Type | Notes |
|---|---|---|
| `callerSubject` | `String` | Authenticated user identity |
| `recentPayments` | `List<RecentPaymentSummary>` | Read-only summaries from `payments.push.api` |
| `supportedTopics` | `List<String>` | Static BlitzPay topics allowed for v1 |

### `VoiceAnswer`

| Field | Type | Notes |
|---|---|---|
| `text` | `String` | Final text answer before synthesis |
| `redirected` | `Boolean` | True when the assistant gently redirects unsupported topics |

### `SynthesizedVoiceResponse`

| Field | Type | Notes |
|---|---|---|
| `audioBytes` | `ByteArray` | Synthesized response body |
| `contentType` | `String` | `audio/mpeg` in v1 |
| `transcript` | `String` | Returned only within the service layer for logging/diagnostics |
| `answerText` | `String` | Returned only within the service layer for logging/diagnostics |

## Public cross-module models in `payments.push.api`

### `RecentPaymentSummary`

| Field | Type | Notes |
|---|---|---|
| `paymentRequestId` | `UUID` | Stable reference for the payment |
| `status` | `PaymentStatusCode` | Current status |
| `updatedAt` | `Instant` | Latest known status change |
| `orderId` | `String?` | Optional commerce reference if available |
| `amountMinorUnits` | `Long?` | Optional summary data if already captured by payment creation flow |
| `currency` | `String?` | Optional ISO currency code |

### `PaymentVoiceContextGateway`

Read-only API exposed from `payments.push.api`.

```kotlin
interface PaymentVoiceContextGateway {
    fun findRecentPaymentsBySubject(subject: String, limit: Int = 5): List<RecentPaymentSummary>
}
```

## Payment persistence change in `payments.push`

### Existing owning table to enrich

`push_payment_status`

### New/updated columns

| Column | Type | Notes |
|---|---|---|
| `payer_ref` | `VARCHAR(512)` | Authenticated caller subject captured from payment initiation |
| `order_id` | `VARCHAR(128)` | Optional carry-through for more natural spoken summaries |

These fields stay owned by `payments.push`, use Liquibase, and keep the `push_` table prefix required by the constitution.

## Voice configuration model

### `VoiceProperties`

| Property | Purpose |
|---|---|
| `blitzpay.voice.enabled` | Feature toggle for deployment control |
| `blitzpay.voice.base-url` | Provider endpoint base URL |
| `blitzpay.voice.api-key` | Secret supplied by environment |
| `blitzpay.voice.transcription-model` | Configured model ID for speech-to-text |
| `blitzpay.voice.chat-model` | Configured model ID for answer generation |
| `blitzpay.voice.synthesis-model` | Configured model ID for text-to-speech |
| `blitzpay.voice.max-duration-seconds` | Maximum accepted recording duration |
| `blitzpay.voice.max-upload-bytes` | Maximum accepted upload size |
| `blitzpay.voice.accepted-content-types` | Allowed input audio MIME types |
| `blitzpay.voice.output-content-type` | Output audio type, `audio/mpeg` in v1 |

## Error model

All failures return RFC 9457 `ProblemDetail` with a stable `reason` extension.

| Reason | HTTP Status | Trigger |
|---|---|---|
| `MISSING_AUTHORIZATION` | 401 | Missing or unusable bearer token |
| `MISSING_AUDIO` | 400 | Multipart request has no `audio` part |
| `UNSUPPORTED_AUDIO_FORMAT` | 415 | MIME type not on allow-list |
| `PAYLOAD_TOO_LARGE` | 413 | Upload exceeds configured byte limit |
| `AUDIO_TOO_SHORT` | 400 | Provider or validator reports < 1 second |
| `AUDIO_TOO_LONG` | 400 | Provider or validator reports > configured limit |
| `NO_SPEECH_DETECTED` | 400 | Transcription yields no usable speech |
| `UPSTREAM_AI_ERROR` | 502 | Provider error during transcription, reasoning, or synthesis |
