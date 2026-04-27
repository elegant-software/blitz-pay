# API Contract: POST /v1/voice/query

**Module**: `voice`
**Version**: v1 (extended, not versioned separately)
**Changed in**: 012-voice-ai-product-rag

---

## Request

```
POST /v1/voice/query
Content-Type: multipart/form-data
Authorization: Bearer <jwt>
```

| Part | Type | Required | Notes |
|------|------|----------|-------|
| `audio` | file | Yes | Audio recording. Accepted MIME types: `audio/mpeg`, `audio/mp4`, `audio/webm`, `audio/wav`, `audio/ogg`, `audio/m4a`. Max size: 25 MB. |
| `merchantId` | string (UUID) | No | Merchant scope for product search. Both `merchantId` and `branchId` must be provided together to trigger product reasoning. |
| `branchId` | string (UUID) | No | Branch scope for product search. |

---

## Response

`Content-Type: application/json`

The response contains a `type` discriminator field. Clients MUST inspect `type` before reading other fields.

### `TRANSCRIPT` — transcript-only mode (no merchant context provided)

```json
{
  "type": "TRANSCRIPT",
  "transcript": "<transcribed text>",
  "language": "<BCP-47 code or null>"
}
```

### `PRODUCT_RESULT` — one or more products matched

```json
{
  "type": "PRODUCT_RESULT",
  "requestedQuantity": 1,
  "products": [
    {
      "productId": "<UUID>",
      "branchId": "<UUID>",
      "name": "<product name>",
      "description": "<description or null>",
      "unitPrice": 3.50,
      "imageUrl": "<pre-signed URL or null>"
    }
  ]
}
```

- `products` is a ranked list of 1–5 items ordered by match confidence (best first).
- `requestedQuantity` is the quantity stated by the customer (`null` if not stated; default to 1 on client side).

### `NO_MATCH` — no product matched or request not understood

```json
{
  "type": "NO_MATCH",
  "message": "I didn't understand that request — try browsing the product screen."
}
```

---

## Error Responses

All errors use RFC 9457 Problem Details (`application/problem+json`).

| Scenario | HTTP Status | `reason` value |
|----------|-------------|----------------|
| Missing or invalid `Authorization` header | 401 | `MISSING_AUTHORIZATION` |
| No audio part in multipart body | 400 | `MISSING_AUDIO` |
| Unsupported audio MIME type | 415 | `UNSUPPORTED_AUDIO_FORMAT` |
| Audio file exceeds size limit | 413 | `PAYLOAD_TOO_LARGE` |
| Audio too short (< 1 s) | 400 | `AUDIO_TOO_SHORT` |
| Audio exceeds max duration | 400 | `AUDIO_TOO_LONG` |
| No speech detected by Whisper | 400 | `NO_SPEECH_DETECTED` |
| Whisper or Ollama upstream failure | 502 | `UPSTREAM_AI_ERROR` |

---

## Contract Tests Required

| Scenario | Test method |
|----------|-------------|
| Valid audio, no merchant context → TRANSCRIPT response | existing — verify `type = TRANSCRIPT` field added |
| Valid audio + merchantId + branchId, exact product name → PRODUCT_RESULT (single) | new |
| Valid audio + merchantId + branchId, partial match → PRODUCT_RESULT (multiple, ≤5) | new |
| Valid audio + merchantId + branchId, no match → NO_MATCH | new |
| Missing Authorization | existing |
| Missing audio | existing |
| No speech detected | existing |
| Upstream AI failure | existing |
