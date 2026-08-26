from http.server import BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs
from urllib.request import build_opener, Request, HTTPCookieProcessor
from http.cookiejar import CookieJar
import json

PREVIEW_BASE = "https://shoshi-luna-executive-agency-fwmx8pqsl-shahrnfs12-1551.vercel.app"
SHARE_TOKEN = "iXJx6dUyZs3YtXGBkS0a8JMA5JmHW1FJ"
APK_PATH = "/ShoshiLuna-latest.apk"
MAX_CHUNK = 3 * 1024 * 1024


def authenticated_opener():
    jar = CookieJar()
    opener = build_opener(HTTPCookieProcessor(jar))
    # Visit the temporary share URL first so Vercel sets the SSO bypass cookie.
    auth_url = f"{PREVIEW_BASE}/?_vercel_share={SHARE_TOKEN}"
    req = Request(auth_url, headers={"User-Agent": "ShoshiLuna-APK-Bridge/1.0"})
    with opener.open(req, timeout=20) as response:
        response.read(1024)
    return opener


def fetch_range(start: int, end: int):
    opener = authenticated_opener()
    req = Request(
        PREVIEW_BASE + APK_PATH,
        headers={
            "User-Agent": "ShoshiLuna-APK-Bridge/1.0",
            "Range": f"bytes={start}-{end}",
            "Accept": "application/vnd.android.package-archive,application/octet-stream,*/*",
        },
    )
    with opener.open(req, timeout=30) as response:
        data = response.read(end - start + 1)
        content_range = response.headers.get("Content-Range")
        content_length = response.headers.get("Content-Length")
        content_type = response.headers.get("Content-Type", "application/octet-stream")
        status = getattr(response, "status", 200)
    total = None
    if content_range and "/" in content_range:
        try:
            total = int(content_range.rsplit("/", 1)[1])
        except ValueError:
            pass
    if total is None and status == 200 and content_length:
        try:
            total = int(content_length)
        except ValueError:
            pass
    return data, total, content_type, status


class handler(BaseHTTPRequestHandler):
    def do_GET(self):
        try:
            query = parse_qs(urlparse(self.path).query)
            if query.get("meta", [""])[0] == "1":
                data, total, _, _ = fetch_range(0, 0)
                if not total:
                    raise RuntimeError("Unable to determine APK size")
                body = json.dumps({"size": total, "chunk": MAX_CHUNK}).encode("utf-8")
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Cache-Control", "no-store")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
                return

            start = int(query.get("start", ["0"])[0])
            end = int(query.get("end", [str(start + MAX_CHUNK - 1)])[0])
            if start < 0 or end < start or (end - start + 1) > MAX_CHUNK:
                raise ValueError("Invalid byte range")

            data, total, _, _ = fetch_range(start, end)
            self.send_response(206)
            self.send_header("Content-Type", "application/octet-stream")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(data)))
            if total:
                self.send_header("Content-Range", f"bytes {start}-{start + len(data) - 1}/{total}")
            self.end_headers()
            self.wfile.write(data)
        except Exception as exc:
            body = json.dumps({"error": str(exc)}).encode("utf-8")
            self.send_response(502)
            self.send_header("Content-Type", "application/json")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
