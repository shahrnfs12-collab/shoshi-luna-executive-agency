import base64
import json
import os
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler

PREVIEW_BASE = "https://shoshi-luna-executive-agency-chcf6y07o-shahrnfs12-1551.vercel.app"
SHARE_TOKEN = "8KJ4PYlERjuRlgQLOMWu49SEwjfEHIj3"
APK_PATH = "/ShoshiLuna-2.5.3-full-runtime.apk"
CHUNK_SIZE = 2 * 1024 * 1024


class handler(BaseHTTPRequestHandler):
    def do_GET(self):
        try:
            query = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
            request = urllib.request.Request(
                PREVIEW_BASE + APK_PATH,
                headers={"x-vercel-protection-bypass": SHARE_TOKEN},
            )
            with urllib.request.urlopen(request, timeout=30) as response:
                data = response.read()
            if not data.startswith(b"PK"):
                raise ValueError("Upstream artifact is not an APK")

            if query.get("meta") == ["1"]:
                payload = {"size": len(data), "chunk_size": CHUNK_SIZE, "chunks": (len(data) + CHUNK_SIZE - 1) // CHUNK_SIZE}
            else:
                index = int(query.get("index", ["0"])[0])
                start = index * CHUNK_SIZE
                payload = {"index": index, "data": base64.b64encode(data[start:start + CHUNK_SIZE]).decode("ascii")}

            body = json.dumps(payload).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(body)
        except Exception as exc:
            body = json.dumps({"error": str(exc)}).encode("utf-8")
            self.send_response(502)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(body)
