# API Contract: Voice Query

**Version**: `v1`  
**Base path**: `/v1/voice`  
**Module**: `com.elegant.software.blitzpay.voice`  
**Auth**: Required bearer token

## POST /v1/voice/query

Submit a single voice recording and receive a synthesized spoken answer in one synchronous request.

### Request

```http
POST /v1/voice/query
Authorization: Bearer <jwt>
Content-Type: multipart/form-data
```

### Multipart parts

| Name | Type | Required | Constraints |
|---|---|---|---|
| `audio` | Binary file | Yes | Accepted types: `audio/mpeg`, `audio/mp4`, `audio/webm`, `audio/wav`, `audio/ogg` |

### Successful response

**Status**: `200 OK`  
**Content-Type**: `audio/mpeg`

Response body is binary MP3 audio.

### Error responses

All error payloads use `application/problem+json` with a `reason` extension.

#### 401 Unauthorized

Used when the bearer token is missing or the token payload does not contain a usable subject.

```json
{
  "title": "Unauthorized",
  "status": 401,
  "detail": "Authenticated access is required.",
  "reason": "MISSING_AUTHORIZATION"
}
```

#### 400 Bad Request - Missing audio

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "Multipart field 'audio' is required.",
  "reason": "MISSING_AUDIO"
}
```

#### 415 Unsupported Media Type

```json
{
  "title": "Unsupported Media Type",
  "status": 415,
  "detail": "Accepted formats: audio/mpeg, audio/mp4, audio/webm, audio/wav, audio/ogg.",
  "reason": "UNSUPPORTED_AUDIO_FORMAT"
}
```

#### 413 Payload Too Large

```json
{
  "title": "Payload Too Large",
  "status": 413,
  "detail": "Audio file exceeds the configured upload limit.",
  "reason": "PAYLOAD_TOO_LARGE"
}
```

#### 400 Bad Request - Too short

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "Recording must be at least 1 second long.",
  "reason": "AUDIO_TOO_SHORT"
}
```

#### 400 Bad Request - Too long

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "Recording exceeds the maximum allowed duration.",
  "reason": "AUDIO_TOO_LONG"
}
```

#### 400 Bad Request - No speech detected

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "No recognizable speech was detected in the uploaded recording.",
  "reason": "NO_SPEECH_DETECTED"
}
```

#### 502 Bad Gateway

```json
{
  "title": "Bad Gateway",
  "status": 502,
  "detail": "Voice processing is temporarily unavailable.",
  "reason": "UPSTREAM_AI_ERROR"
}
```

## Contract test coverage

The contract suite must cover:

1. Valid authenticated request returns `200` with `audio/mpeg`.
2. Missing bearer token returns `401`.
3. Missing `audio` part returns `400` with `MISSING_AUDIO`.
4. Unsupported MIME type returns `415`.
5. Oversized upload returns `413`.
6. No speech detected returns `400`.
7. Upstream provider failure returns `502`.
