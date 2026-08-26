import base64
import json
import urllib.parse
from pathlib import Path
from http.server import BaseHTTPRequestHandler

APK_NAME = "ShoshiLuna-latest.apk"
CHUNK_SIZE = 2 * 1024 * 1024


class handler(BaseHTTPRequestHandler):
    def do_GET(self):
        try:
            query = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
            candidates = [
                Path.cwd() / "apk-out" / APK_NAME,
                Path(__file__).resolve().parent.parent / "apk-out" / APK_NAME,
                Path.cwd() / APK_NAME,
            ]
            apk = next((path for path in candidates if path.is_file()), None)
            if apk is None:
                raise FileNotFoundError("Bundled APK is unavailable")
            data = apk.read_bytes()
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
