#!/usr/bin/env python3
"""Check the native client's SHADOW API contract using Python's standard library.

No media uploads, wardrobe seeding, skill creation or deletion. --exercise-ai
calls the configured model and may incur provider usage. Codes stay in memory.
"""
import argparse
import getpass
import http.cookiejar
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def origin(value):
    url = urllib.parse.urlsplit(value.strip())
    local_http = url.scheme == "http" and url.hostname in ("localhost", "127.0.0.1", "::1")
    if (url.scheme != "https" and not local_http) or not url.hostname or url.username is not None or url.password is not None or url.path not in ("", "/") or url.query or url.fragment:
        raise ValueError("Use an HTTPS origin, or HTTP loopback for a local test; no credentials, path, query or fragment.")
    return value.strip().rstrip("/")


class Client:
    def __init__(self, endpoint):
        self.endpoint = origin(endpoint)
        self.opener = urllib.request.build_opener(NoRedirect(), urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

    def request(self, path, payload=None):
        body = None if payload is None else json.dumps(payload).encode()
        request = urllib.request.Request(self.endpoint + path, data=body, headers={
            "Accept": "application/json", "Content-Type": "application/json",
        })
        with self.opener.open(request, timeout=90) as response:
            return json.load(response)


def require(value, message):
    if not value:
        raise ValueError(message)


def check_source(data, require_ai, label):
    source = data.get("source")
    require(source in ("ai", "mock", "fallback", "ai+fallback"), label + ": unknown/missing source")
    print(label + ": source=" + source)
    if require_ai:
        require(source == "ai", label + ": expected real AI, received demo or fallback")


def check(client, *, exercise_ai=False, require_ai=False, code=None):
    health = client.request("/api/health")
    require(health.get("ok") is True, "Health is not ready")
    require(health.get("mode") in ("ai", "mock"), "Unknown health mode")
    print("health: mode=" + health["mode"])
    if require_ai:
        require(health["mode"] == "ai", "Server is running in mock mode")
    if health.get("access_required"):
        access_code = code if code is not None else getpass.getpass("SHADOW access code: ")
        client.request("/api/access", {"code": access_code.strip()})
    skills = client.request("/api/skills")
    require(isinstance(skills, list), "Skills must be a list")
    for skill in skills:
        require(isinstance(skill.get("id"), str) and skill["id"], "Skill id missing")
        require(isinstance(skill.get("display_name"), str) and skill["display_name"], "Skill title missing")
        require(type(skill.get("steps")) is int, "Skill step count must be an integer")
    print("skills: " + str(len(skills)))
    lesson = None
    if skills:
        lesson = client.request("/api/skills/" + urllib.parse.quote(skills[0]["id"], safe=""))
        require(isinstance(lesson.get("steps"), list), "Lesson steps must be a list")
        for step in lesson["steps"]:
            require(step.get("id") and step.get("instruction"), "Lesson step is incomplete")
    items = client.request("/api/wardrobe/items")
    require(isinstance(items, list), "Wardrobe must be a list")
    for item in items:
        require(item.get("id") and item.get("name"), "Wardrobe item is incomplete")
    print("wardrobe: " + str(len(items)))
    if exercise_ai:
        require(lesson and lesson["steps"], "Cannot exercise Q&A: teach a skill first")
        answer = client.request("/api/ask", {"skill_id": lesson["id"], "step_id": lesson["steps"][0]["id"], "question": "Bước này cần chú ý điều gì?"})
        require(isinstance(answer.get("answer"), str) and answer["answer"].strip(), "Empty answer")
        check_source(answer, require_ai, "ask")
        require(items, "Cannot exercise stylist: add real wardrobe photos first")
        suggestions = client.request("/api/stylist/suggest", {"event": "Đi làm", "notes": ""})
        check_source(suggestions, require_ai, "stylist")
        require(isinstance(suggestions.get("outfits"), list) and suggestions["outfits"], "No outfits returned")
        known = {item["id"] for item in items}
        for outfit in suggestions["outfits"]:
            require(outfit.get("title") and outfit.get("item_ids"), "Outfit is incomplete")
            require(set(outfit["item_ids"]) <= known, "Outfit invented an item outside the wardrobe")
            require(isinstance(outfit.get("items"), list) and outfit["items"], "Outfit items missing")
            require({item.get("id") for item in outfit["items"]} == set(outfit["item_ids"]), "Outfit items do not match ids")
            require(all(item.get("name") for item in outfit["items"]), "Outfit item name missing")
    print("PASS: " + ("API contract and model-response checks" if exercise_ai else "read-only API contract; inference not exercised"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("endpoint")
    parser.add_argument("--exercise-ai", action="store_true")
    parser.add_argument("--require-ai", action="store_true", help="Also exercise AI and reject mock/fallback results")
    args = parser.parse_args()
    try:
        check(Client(args.endpoint), exercise_ai=args.exercise_ai or args.require_ai,
              require_ai=args.require_ai, code=os.environ.get("SHADOW_ACCESS_CODE"))
    except urllib.error.HTTPError as error:
        print("FAIL: HTTP " + str(error.code) + " (check origin, access code and server status)", file=sys.stderr)
        return 1
    except (ValueError, OSError, KeyError, TypeError, AttributeError) as error:
        # Do not print remote payloads, URLs or credentials from transport exceptions.
        message = str(error) if isinstance(error, ValueError) else type(error).__name__
        print("FAIL: " + message, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
