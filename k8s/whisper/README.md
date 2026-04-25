# Whisper Service

Local speech-to-text service that exposes an OpenAI-compatible REST API
(`POST /v1/audio/transcriptions`) for use inside the blitzpay-staging cluster.

## Build & Push

```bash
# Authenticate to GHCR (once)
echo $GITHUB_TOKEN | docker login ghcr.io -u <your-github-username> --password-stdin

# Build
docker build -t ghcr.io/elegant-software/whisper-service:latest k8s/whisper/

# Push
docker push ghcr.io/elegant-software/whisper-service:latest
```

## Deploy to Kubernetes

```bash
kubectl apply -f k8s/whisper.yml

# Watch startup — first run downloads the model (~142 MB for "base"), takes ~1 min
kubectl rollout status deployment/whisper -n blitzpay-staging
```

## Test

**Step 1 — port-forward the service to localhost:**
```bash
kubectl port-forward svc/whisper 8000:8000 -n blitzpay-staging
```

**Step 2 — send a test audio file (use the repo's test.wav):**
```bash
curl http://localhost:8000/v1/audio/transcriptions \
  -F "file=@test.wav;type=audio/wav" \
  -F "model=whisper-1" \
  -F "response_format=verbose_json"
```

Expected response:
```json
{
  "text": "...",
  "language": "en",
  "duration": 3.5
}
```

**Step 3 — test end-to-end with the blitz-pay backend:**
```bash
WHISPER_BASE_URL=http://localhost:8000/v1 ./gradlew bootRun
```

Then call the voice endpoint:
```bash
curl -X POST http://localhost:8080/v1/voice/query \
  -H "Authorization: Bearer <your-jwt>" \
  -F "audio=@test.wav;type=audio/wav"
```

## Configuration

| Env var | Default | Description |
|---------|---------|-------------|
| `WHISPER_MODEL` | `base` | Model size: `tiny`, `base`, `small`, `medium`, `large-v3` |

Model files are cached in the PVC (`whisper-model-cache`) so they survive pod restarts.

## Pointing blitz-pay at this service

Set these env vars in the blitz-pay deployment (instead of using the OpenAI API):

```
WHISPER_BASE_URL=http://whisper:8000/v1
OPENAI_API_KEY=
```

---

## TODO

### Reduce image size by switching to faster-whisper

**Current**: `openai-whisper` pulls PyTorch → image is ~1.5–2 GB.

**Fix**: Replace `openai-whisper` with `faster-whisper` (uses CTranslate2, no PyTorch) → image drops to ~400–500 MB and inference is faster on CPU.

Changes needed:
- `Dockerfile`: replace `openai-whisper` pip dependency with `faster-whisper`
- `server.py`: swap `whisper` API for `faster_whisper.WhisperModel` API
- `whisper.yml`: model name changes from `base` to the HuggingFace ID `Systran/faster-whisper-base`; cache mount path changes to `/root/.cache/huggingface`
