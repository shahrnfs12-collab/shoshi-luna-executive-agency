from __future__ import annotations

from typing import Any

from mcp.server import MCPServer
from mcp.server.transport_security import TransportSecuritySettings

SERVER_NAME = "SHOSHI-LUNA-HeartLink"
mcp = MCPServer(SERVER_NAME)


@mcp.tool()
def heartlink_status() -> dict[str, Any]:
    return {
        "server": SERVER_NAME,
        "protocol": "MCP Streamable HTTP",
        "mode": "REMOTE_SAFE_CONTROL_PLANE",
        "truth_status": "REMOTE_ENDPOINT_ONLY",
        "write_tools_exposed": False,
        "source_read_tools_exposed": False,
        "release_tools_exposed": False,
        "money_movement_exposed": False,
    }


@mcp.tool()
def agency_status() -> dict[str, Any]:
    return {
        "executive_agents": [
            {"name": "SHOSHI", "id": "AGENT-SHOSHI-001", "remote_state": "REGISTERED_NOT_DEVICE_BOUND"},
            {"name": "LUNA", "id": "AGENT-LUNA-002", "remote_state": "REGISTERED_NOT_DEVICE_BOUND"},
        ],
        "remote_execution": "LOCKED_UNTIL_AUTHENTICATED_DEVICE_BINDING",
        "note": "Repository-side agents are registered; phone runtime remains unverified until signed device heartbeat evidence exists.",
    }


@mcp.tool()
def spatial_3d_status() -> dict[str, Any]:
    return {
        "assets_expected": ["app/src/main/assets/models/shoshi.glb", "app/src/main/assets/models/luna.glb"],
        "required_animations": ["Idle", "Talk", "Walk", "Listen", "Point", "Present", "TurnLeft", "TurnRight"],
        "state": "REQUIRES_DEVICE_VALIDATION",
    }


@mcp.tool()
def safety_status() -> dict[str, Any]:
    return {
        "explicit_owner_approval_for_sensitive_actions": True,
        "destructive_tools_exposed": False,
        "signing_keys_exposed": False,
        "secrets_exposed": False,
        "publishing_exposed": False,
        "purchases_exposed": False,
        "money_movement_exposed": False,
        "fail_closed": True,
    }


security = TransportSecuritySettings(enable_dns_rebinding_protection=False)
app = mcp.streamable_http_app(transport_security=security)
