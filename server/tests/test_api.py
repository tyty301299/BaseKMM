import asyncio
import base64
from datetime import datetime, timezone
from types import SimpleNamespace

from fastapi.testclient import TestClient
from google.genai import types

from app.api import LIVE_MODEL, create_app, live_token_config
from app.verify import SCAN_CONFIG, UNCLEAR_MESSAGE, VERIFY_CONFIG, MockVerifier, parse_result, parse_scan, to_zone

IMAGE = base64.b64encode(b"jpeg-bytes").decode()


def fake_gemini(generate=None, create_token=None):
    return SimpleNamespace(aio=SimpleNamespace(
        models=SimpleNamespace(generate_content=generate),
        auth_tokens=SimpleNamespace(create=create_token),
    ))


def test_parse_result_treats_bad_output_as_unclear():
    unclear = {"status": "UNCLEAR", "message": UNCLEAR_MESSAGE, "misplaced": []}
    assert parse_result("not json") == unclear
    assert parse_result(None) == unclear
    assert parse_result("[1]") == unclear
    assert parse_result('{"status":"MAYBE","message":"x"}') == unclear
    assert parse_result('{"status":"DONE","message":""}') == unclear
    assert parse_result('{"status":"DONE","message":"ok"}') == {"status": "DONE", "message": "ok", "misplaced": []}


def test_verify_rejects_bad_requests():
    client = TestClient(create_app(None))
    assert client.post("/api/verify", json={"stepId": "nope", "imageBase64": IMAGE}).status_code == 400
    assert client.post("/api/verify", json={"stepId": "cups"}).status_code == 400
    assert client.post("/api/verify", json={"stepId": "cups", "imageBase64": "%%%"}).status_code == 400


def test_mock_fails_a_step_once_then_passes():
    client = TestClient(create_app(None))
    body = {"stepId": "bowls", "imageBase64": IMAGE}
    assert client.post("/api/verify", json=body).json()["status"] == "NOT_DONE"
    assert client.post("/api/verify", json=body).json()["status"] == "DONE"


def test_live_token_unavailable_in_mock_mode():
    assert TestClient(create_app(None)).post("/api/live-token").status_code == 503


def test_verify_sends_photo_and_returns_verdict():
    seen = {}

    async def generate(**kwargs):
        seen.update(kwargs)
        return SimpleNamespace(text='{"status":"NOT_DONE","message":"Còn một cốc ở giữa bàn."}')

    client = TestClient(create_app(fake_gemini(generate=generate)))
    response = client.post("/api/verify", json={"stepId": "cups", "imageBase64": IMAGE})
    assert response.json() == {"status": "NOT_DONE", "message": "Còn một cốc ở giữa bàn.", "misplaced": []}
    assert seen["contents"][0].inline_data.data == b"jpeg-bytes"


def test_verify_returns_502_when_gemini_hangs():
    async def generate(**_kwargs):
        await asyncio.sleep(5)

    client = TestClient(create_app(fake_gemini(generate=generate), verify_timeout=0.02))
    assert client.post("/api/verify", json={"stepId": "cups", "imageBase64": IMAGE}).status_code == 502


def test_live_token_returns_only_token_and_model():
    async def create_token(config):
        return SimpleNamespace(name="auth_tokens/abc")

    client = TestClient(create_app(fake_gemini(create_token=create_token)))
    assert client.post("/api/live-token").json() == {"token": "auth_tokens/abc", "model": LIVE_MODEL}


def test_requests_match_the_sdk_types():
    config = types.CreateAuthTokenConfig.model_validate(live_token_config(datetime.now(timezone.utc)))
    assert config.uses == 1
    assert config.live_connect_constraints.model == LIVE_MODEL
    assert config.http_options.api_version == "v1alpha"
    assert types.GenerateContentConfig.model_validate(VERIFY_CONFIG).response_json_schema
    assert types.GenerateContentConfig.model_validate(SCAN_CONFIG).response_json_schema


def test_to_zone_converts_gemini_order_and_rejects_bad_boxes():
    assert to_zone([100, 200, 500, 800]) == [0.2, 0.1, 0.8, 0.5]
    assert to_zone([-50, 0, 1200, 1000]) == [0.0, 0.0, 1.0, 1.0]
    assert to_zone([500, 200, 100, 800]) is None
    assert to_zone([1, 2, 3]) is None
    assert to_zone("x") is None
    assert to_zone([True, 2, 3, 4]) is None


def test_parse_result_returns_misplaced_only_when_not_done():
    text = '{"status":"NOT_DONE","message":"m","misplaced":[[100,200,500,800],[1,2]]}'
    assert parse_result(text)["misplaced"] == [[0.2, 0.1, 0.8, 0.5]]
    done = '{"status":"DONE","message":"m","misplaced":[[100,200,500,800]]}'
    assert parse_result(done)["misplaced"] == []


def test_parse_scan_keeps_only_valid_objects():
    text = """{"objects":[
        {"label":"cốc","box_2d":[100,200,500,800]},
        {"label":"xe hơi","box_2d":[100,200,500,800]},
        {"label":"bát","box_2d":[1,2]}
    ],"target_found":true,"target_box_2d":[0,0,500,500]}"""
    assert parse_scan(text) == {
        "target": [0.0, 0.0, 0.5, 0.5],
        "objects": [{"label": "cốc", "box": [0.2, 0.1, 0.8, 0.5]}],
    }
    assert parse_scan("nope") == {"target": None, "objects": []}
    assert parse_scan('{"objects":[],"target_found":false,"target_box_2d":[0,0,5,5]}')["target"] is None


def test_scan_endpoint_labels_mock_results_as_fake():
    client = TestClient(create_app(None))
    body = client.post("/api/scan", json={"stepId": "cups", "imageBase64": IMAGE}).json()
    assert body["target"]
    assert all("giả lập" in item["label"] for item in body["objects"])
    assert client.post("/api/scan", json={"stepId": "nope", "imageBase64": IMAGE}).status_code == 400


def test_scan_sends_photo_to_gemini():
    seen = {}

    async def generate(**kwargs):
        seen.update(kwargs)
        return SimpleNamespace(text='{"objects":[{"label":"thìa","box_2d":[0,0,100,100]}],"target_found":false}')

    client = TestClient(create_app(fake_gemini(generate=generate)))
    body = client.post("/api/scan", json={"stepId": "spoons", "imageBase64": IMAGE}).json()
    assert body == {"target": None, "objects": [{"label": "thìa", "box": [0.0, 0.0, 0.1, 0.1]}]}
    assert seen["contents"][0].inline_data.data == b"jpeg-bytes"


def test_mock_verifier_counts_each_device_separately():
    verify = MockVerifier()
    assert verify("phone", "cups")["status"] == "NOT_DONE"
    assert verify("emulator", "cups")["status"] == "NOT_DONE"
    assert verify("phone", "cups")["status"] == "DONE"
