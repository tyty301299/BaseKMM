"""Offline tests of the smoke checker itself, not tests of a running AI/backend."""
import contextlib
import io
import unittest
from shadow_smoke import check, origin


class FixtureClient:
    def __init__(self, source="ai", item_id="shirt"):
        self.calls = []
        self.source = source
        self.item_id = item_id

    def request(self, path, payload=None):
        self.calls.append((path, payload))
        return {
            "/api/health": {"ok": True, "mode": "ai", "access_required": True},
            "/api/access": {"ok": True},
            "/api/skills": [{"id": "stand", "display_name": "Lắp giá", "steps": 1}],
            "/api/skills/stand": {"id": "stand", "steps": [{"id": "one", "instruction": "Đặt đế"}]},
            "/api/wardrobe/items": [{"id": "shirt", "name": "Áo"}],
            "/api/ask": {"answer": "Đặt đế lên bàn.", "source": self.source},
            "/api/stylist/suggest": {"source": self.source, "outfits": [{
                "title": "Đi làm", "item_ids": [self.item_id], "items": [{"id": self.item_id, "name": "Áo"}],
            }]},
        }[path]


class SmokeTests(unittest.TestCase):
    def run_check(self, client, **options):
        with contextlib.redirect_stdout(io.StringIO()):
            check(client, code="private", **options)

    def test_authentication_precedes_protected_requests(self):
        client = FixtureClient()
        self.run_check(client)
        self.assertEqual(client.calls[1], ("/api/access", {"code": "private"}))
        self.assertFalse(any(path == "/api/ask" for path, _ in client.calls))

    def test_model_checks_accept_ai(self):
        self.run_check(FixtureClient(), exercise_ai=True, require_ai=True)

    def test_real_ai_check_rejects_fallback_and_mock(self):
        for source in ("mock", "fallback", "ai+fallback"):
            with self.subTest(source=source), self.assertRaisesRegex(ValueError, "expected real AI"):
                self.run_check(FixtureClient(source=source), exercise_ai=True, require_ai=True)

    def test_unknown_sources_are_rejected(self):
        with self.assertRaisesRegex(ValueError, "unknown/missing source"):
            self.run_check(FixtureClient(source="unknown"), exercise_ai=True)

    def test_hallucinated_garments_are_rejected(self):
        with self.assertRaisesRegex(ValueError, "invented an item"):
            self.run_check(FixtureClient(item_id="invented"), exercise_ai=True)

    def test_origin_rules(self):
        self.assertEqual(origin(" https://shadow.example/ "), "https://shadow.example")
        self.assertEqual(origin("http://127.0.0.1:8000"), "http://127.0.0.1:8000")
        for value in ("http://public.example", "https://user:secret@shadow.example", "https://shadow.example/api",
                      "https://shadow.example/?code=secret", "https://shadow.example/#/teach", "bad"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                origin(value)


if __name__ == "__main__":
    unittest.main()
