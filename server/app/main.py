import os

from dotenv import load_dotenv
from google import genai

from app.api import create_app

load_dotenv()

if os.environ.get("MOCK_GEMINI") == "1":
    client = None
else:
    api_key = os.environ.get("GEMINI_API_KEY")
    if not api_key:
        raise SystemExit("Missing GEMINI_API_KEY. Copy .env.example to .env, or set MOCK_GEMINI=1.")
    client = genai.Client(api_key=api_key)

app = create_app(client)
