import os
import tempfile
import whisper as whisper_lib
from fastapi import FastAPI, File, Form, UploadFile
from fastapi.responses import JSONResponse

app = FastAPI()

_model = whisper_lib.load_model(os.getenv("WHISPER_MODEL", "base"))


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/v1/audio/transcriptions")
async def transcribe(
    file: UploadFile = File(...),
    model: str = Form(default="whisper-1"),
    response_format: str = Form(default="json"),
    language: str = Form(default=None),
):
    suffix = os.path.splitext(file.filename or "audio")[1] or ".wav"
    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
        tmp.write(await file.read())
        tmp_path = tmp.name

    try:
        result = _model.transcribe(tmp_path, language=language or None)
        text = result["text"].strip()
        segments = result.get("segments", [])
        duration = segments[-1]["end"] if segments else None

        if response_format == "verbose_json":
            return JSONResponse({
                "text": text,
                "language": result.get("language"),
                "duration": duration,
                "segments": segments,
            })
        return JSONResponse({"text": text})
    finally:
        os.unlink(tmp_path)
