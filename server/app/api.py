import asyncio
import base64
import binascii
import logging
from datetime import datetime, timedelta, timezone

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from app.verify import CRITERIA, MOCK_SCAN, TARGETS, MockVerifier, scan_scene, verify_step

LIVE_MODEL = "gemini-3.8-live"
VISION_MODEL = "gemini-3.8-flash"
VERIFY_TIMEOUT_SECONDS = 20.0
MAX_IMAGE_BASE64_CHARS = 8_000_000

logger = logging.getLogger("kitchenmate")


class VerifyRequest(BaseModel):
    stepId: str
    imageBase64: str = Field(min_length=1, max_length=MAX_IMAGE_BASE64_CHARS)


def decode_image(value):
    try:
        return base64.b64decode(value, validate=True)
    except binascii.Error:
        return None


def error(status, code):
    return JSONResponse(status_code=status, content={"error": code})


def live_token_config(now):
    return {
        "uses": 1,
        "expire_time": now + timedelta(minutes=30),
        "new_session_expire_time": now + timedelta(minutes=1),
        "live_connect_constraints": {
            "model": LIVE_MODEL,
            "config": {"response_modalities": ["AUDIO"]},
        },
        # Ephemeral tokens are only served by v1alpha; vision calls keep the default version.
        "http_options": {"api_version": "v1alpha"},
    }


def create_app(ai, verify_timeout=VERIFY_TIMEOUT_SECONDS):
    """`ai` is a google.genai Client, or None in mock mode."""
    app = FastAPI(title="KitchenMate")
    mock_verify = MockVerifier()

    @app.exception_handler(RequestValidationError)
    async def invalid_request(_request, _exc):
        return error(400, "invalid_request")

    @app.get("/health")
    async def health():
        return {"ok": True, "mock": ai is None}

    # Single-use token, so the long-lived API key never reaches the phone.
    @app.post("/api/live-token")
    async def live_token():
        if ai is None:
            return error(503, "live_unavailable")
        try:
            token = await ai.aio.auth_tokens.create(config=live_token_config(datetime.now(timezone.utc)))
        except Exception:
            logger.exception("live-token failed")
            return error(502, "token_failed")
        return {"token": token.name, "model": LIVE_MODEL}

    @app.post("/api/verify")
    async def verify(request: VerifyRequest, http: Request):
        criterion = CRITERIA.get(request.stepId)
        image = decode_image(request.imageBase64)
        if criterion is None or not image:
            return error(400, "invalid_request")
        if ai is None:
            return mock_verify(http.client.host, request.stepId)
        try:
            return await asyncio.wait_for(verify_step(ai, VISION_MODEL, criterion, image), verify_timeout)
        except Exception:
            logger.exception("verify failed")
            return error(502, "verify_failed")

    @app.post("/api/scan")
    async def scan(request: VerifyRequest):
        target = TARGETS.get(request.stepId)
        image = decode_image(request.imageBase64)
        if target is None or not image:
            return error(400, "invalid_request")
        if ai is None:
            return MOCK_SCAN
        try:
            return await asyncio.wait_for(scan_scene(ai, VISION_MODEL, target, image), verify_timeout)
        except Exception:
            logger.exception("scan failed")
            return error(502, "scan_failed")

    return app
