from http.server import BaseHTTPRequestHandler
from urllib.request import Request, urlopen
from urllib.error import HTTPError
import hashlib
import json
import time
import uuid

AGENT_ID = "codex-1-cloud"
DISPLAY_NAME = "CODEX-1 • Shoshi Luna Cloud Agent"
OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"
MAX_BODY = 64 * 1024


class handler(BaseHTTPRequestHandler):
    def _send(self, status, payload):
        body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        self._send(200, self._identity())

    def do_POST(self):
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length <= 0 or length > MAX_BODY:
                return self._send(413, {"error": "invalid_request_size"})
            data = json.loads(self.rfile.read(length))
            action = data.get("action")
            if action == "handshake":
                return self._send(200, self._identity())
            if action != "task":
                return self._send(400, {"error": "unsupported_action"})
            token = self.headers.get("Authorization", "")
            if not token.startswith("Bearer sk-or-"):
                return self._send(401, {"error": "openrouter_key_required"})
            task = str(data.get("task", "")).strip()
            if not task:
                return self._send(400, {"error": "task_required"})
            request_id = str(uuid.uuid4())
            upstream = {
                "model": "openrouter/free",
                "messages": [
                    {"role": "system", "content": str(data.get("system", ""))},
                    {"role": "user", "content": "Mission: %s\nPurpose: %s\nTask: %s" % (
                        data.get("mission_id", ""), data.get("purpose", ""), task
                    )},
                ],
            }
            req = Request(OPENROUTER_URL, data=json.dumps(upstream).encode("utf-8"), headers={
                "Authorization": token,
                "Content-Type": "application/json",
                "HTTP-Referer": "https://shoshi-luna-executive-agency-shahrnfs12-1551.vercel.app",
                "X-Title": "SHOSHI LUNA CODEX-1",
                "User-Agent": "ShoshiLuna-Codex1/1.0",
            }, method="POST")
            with urlopen(req, timeout=100) as response:
                result = json.loads(response.read())
            text = result["choices"][0]["message"]["content"]
            evidence = hashlib.sha256((request_id + text).encode("utf-8")).hexdigest()[:24]
            self._send(200, {"agent_id": AGENT_ID, "request_id": request_id, "text": text, "evidence_id": evidence})
        except HTTPError as exc:
            self._send(exc.code, {"error": "provider_error", "status": exc.code})
        except Exception as exc:
            self._send(502, {"error": "agent_gateway_error", "detail": exc.__class__.__name__})

    def _identity(self):
        now = int(time.time())
        nonce = str(uuid.uuid4())
        evidence = hashlib.sha256((AGENT_ID + nonce + str(now)).encode("utf-8")).hexdigest()[:24]
        return {
            "accepted": True,
            "agent_id": AGENT_ID,
            "display_name": DISPLAY_NAME,
            "protocol": "shoshi-agent-v1",
            "capabilities": ["research", "analysis", "coding", "debugging", "verification", "orchestration"],
            "side_effects": "owner_approval_required",
            "timestamp": now,
            "nonce": nonce,
            "evidence_id": evidence,
        }
