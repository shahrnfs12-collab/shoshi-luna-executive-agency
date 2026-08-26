from http.server import BaseHTTPRequestHandler
import json


class handler(BaseHTTPRequestHandler):
    def do_GET(self):
        payload = {
            "service": "SHOSHI-LUNA-HeartLink",
            "status": "online",
            "mcp_route": "/mcp",
            "agents": {
                "SHOSHI": "REGISTERED_NOT_DEVICE_BOUND",
                "LUNA": "REGISTERED_NOT_DEVICE_BOUND",
            },
            "device_runtime": "UNVERIFIED",
            "spatial_3d": "REQUIRES_DEVICE_VALIDATION",
            "safety": "FAIL_CLOSED",
        }
        body = json.dumps(payload, separators=(",", ":")).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)
