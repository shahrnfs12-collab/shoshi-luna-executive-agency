from __future__ import annotations

from typing import Any

from mcp.server import MCPServer
from mcp.server.transport_security import TransportSecuritySettings

SERVER_NAME = "SHOSHI-LUNA-HeartLink"

mcp = MCPServer(SERVER_NAME)


@mcp.tool()
def heartlink_status() -> dict[str, Any]:
    """Return the public deployment status of the SHOSHI LUNA HeartLink bridge."""
    return {
        "server": SERVER_NAME,
        "protocol": "MCP Streamable HTTP",
        "mode": "REMOTE_SAFE_BOOTSTRAP",
        "write_tools_exposed": False,
        "source_read_tools_exposed": False,
        "release_tools_exposed": False,
        "truth_status": "REMOTE_ENDPOINT_ONLY",
        "note": "This public bootstrap intentionally exposes no private repository content. Authenticated project tools remain locked until the private auth boundary is configured and verified.",
    }


@mcp.tool()
def heartlink_capabilities() -> dict[str, Any]:
    """Describe the bounded capabilities prepared for the private HeartLink bridge."""
    return {
        "prepared": [
            "inspect_project",
            "search_code",
            "read_file",
            "proposal_journal",
            "explicit_owner_approval_gate",
            "evidence_recording",
        ],
        "remote_enabled_now": ["heartlink_status", "heartlink_capabilities"],
        "remote_locked_until_auth": ["inspect_project", "search_code", "read_file", "write_or_build_operations"],
    }


# Vercel terminates HTTPS and controls the Host header before this ASGI app.
# Keep repository/source tools disabled on this public bootstrap endpoint.
security = TransportSecuritySettings(enable_dns_rebinding_protection=False)
app = mcp.streamable_http_app(transport_security=security)
