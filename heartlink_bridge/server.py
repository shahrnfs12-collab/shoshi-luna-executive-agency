from __future__ import annotations

import hashlib
import os
from pathlib import Path
from typing import Any

from mcp.server import MCPServer

SERVER_NAME = "SHOSHI-LUNA-HeartLink"
WORKSPACE = Path(os.environ.get("HEARTLINK_WORKSPACE", Path(__file__).resolve().parents[1])).resolve()
MAX_READ_BYTES = 256_000
MAX_SEARCH_RESULTS = 100
FORBIDDEN_PARTS = {
    ".git",
    ".gradle",
    ".idea",
    "build",
    "secrets",
    "secret",
    "credentials",
    "local.properties",
}
FORBIDDEN_SUFFIXES = {".jks", ".keystore", ".p12", ".pfx", ".pem", ".key"}

mcp = MCPServer(SERVER_NAME)


def _safe_path(relative_path: str) -> Path:
    if not relative_path or relative_path.startswith(("/", "\\")):
        raise ValueError("A workspace-relative path is required")
    candidate = (WORKSPACE / relative_path).resolve()
    try:
        candidate.relative_to(WORKSPACE)
    except ValueError as exc:
        raise ValueError("Path escapes the HeartLink workspace") from exc

    lowered_parts = {p.lower() for p in candidate.relative_to(WORKSPACE).parts}
    if lowered_parts & FORBIDDEN_PARTS or candidate.suffix.lower() in FORBIDDEN_SUFFIXES:
        raise PermissionError("Secret, signing, build-cache, and credential paths are outside HeartLink")
    return candidate


def _is_allowed_file(path: Path) -> bool:
    if not path.is_file() or path.is_symlink():
        return False
    try:
        rel = path.resolve().relative_to(WORKSPACE)
    except ValueError:
        return False
    parts = {p.lower() for p in rel.parts}
    if parts & FORBIDDEN_PARTS or path.suffix.lower() in FORBIDDEN_SUFFIXES:
        return False
    return True


@mcp.tool()
def heartlink_status() -> dict[str, Any]:
    """Return the verified local state of the SHOSHI LUNA HeartLink bridge."""
    return {
        "server": SERVER_NAME,
        "protocol": "MCP Streamable HTTP",
        "mode": "READ_ONLY_FAIL_CLOSED",
        "workspace_present": WORKSPACE.is_dir(),
        "workspace_name": WORKSPACE.name,
        "write_tools_exposed": False,
        "release_tools_exposed": False,
        "truth_status": "LOCAL_REAL",
        "note": "Remote trust is not claimed until the deployed endpoint and authentication are runtime-verified.",
    }


@mcp.tool()
def inspect_project(max_entries: int = 250) -> dict[str, Any]:
    """List a bounded, non-secret view of the SHOSHI LUNA project tree."""
    limit = max(1, min(max_entries, 1000))
    entries: list[dict[str, Any]] = []
    for path in sorted(WORKSPACE.rglob("*")):
        if len(entries) >= limit:
            break
        if not _is_allowed_file(path):
            continue
        rel = path.relative_to(WORKSPACE).as_posix()
        entries.append({"path": rel, "size": path.stat().st_size})
    return {"workspace": WORKSPACE.name, "entries": entries, "truncated": len(entries) >= limit}


@mcp.tool()
def read_file(path: str, max_bytes: int = 64_000) -> dict[str, Any]:
    """Read a bounded UTF-8 text file from the project; secrets/signing paths are blocked."""
    target = _safe_path(path)
    if not _is_allowed_file(target):
        raise PermissionError("File is not inside the readable HeartLink boundary")
    limit = max(1, min(max_bytes, MAX_READ_BYTES))
    raw = target.read_bytes()
    chunk = raw[:limit]
    try:
        text = chunk.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise ValueError("Only UTF-8 text files are exposed") from exc
    return {
        "path": target.relative_to(WORKSPACE).as_posix(),
        "content": text,
        "truncated": len(raw) > limit,
        "sha256": hashlib.sha256(raw).hexdigest(),
        "size": len(raw),
    }


@mcp.tool()
def search_code(query: str, max_results: int = 40) -> dict[str, Any]:
    """Search non-secret UTF-8 project files for a literal string with bounded results."""
    needle = query.strip()
    if not needle:
        raise ValueError("query is required")
    limit = max(1, min(max_results, MAX_SEARCH_RESULTS))
    results: list[dict[str, Any]] = []
    for path in sorted(WORKSPACE.rglob("*")):
        if len(results) >= limit or not _is_allowed_file(path):
            continue
        if path.stat().st_size > MAX_READ_BYTES:
            continue
        try:
            lines = path.read_text(encoding="utf-8").splitlines()
        except (UnicodeDecodeError, OSError):
            continue
        for line_no, line in enumerate(lines, start=1):
            if needle.casefold() in line.casefold():
                results.append({
                    "path": path.relative_to(WORKSPACE).as_posix(),
                    "line": line_no,
                    "preview": line[:500],
                })
                if len(results) >= limit:
                    break
    return {"query": needle, "results": results, "truncated": len(results) >= limit}


def main() -> None:
    # The SDK's Streamable HTTP transport is the production transport. Deployment MUST add
    # an authenticated ingress/OAuth resource-server boundary before exposing private source.
    mcp.run(transport="streamable-http", stateless_http=True, json_response=True)


if __name__ == "__main__":
    main()
