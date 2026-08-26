# SHOSHI LUNA HeartLink MCP Bridge

This directory is the ChatGPT/MCP bridge for the existing SHOSHI LUNA Executive Agency project.

## What is implemented

- MCP 2.0 Streamable HTTP server (`/mcp`).
- Read-only project inspection.
- Bounded UTF-8 file reads with SHA-256 evidence.
- Bounded literal code search.
- Path traversal, signing keys, credential files, secrets, build caches and symlinks are excluded.
- No patch, shell, release, purchase, publishing or money-movement tools are exposed.
- Truth status is explicit: local bridge code is `LOCAL_REAL`; it is not `VERIFIED_REMOTE` until a deployed endpoint and authentication have been tested end to end.

This matches the Android-side `core/heartlink` contract, where changes require a snapshot and explicit approval before execution.

## Local verification

```bash
cd heartlink_bridge
python -m venv .venv
. .venv/bin/activate
pip install -e .
HEARTLINK_WORKSPACE=.. python server.py
```

The MCP endpoint is `http://127.0.0.1:8000/mcp` by default. Test it with the MCP Inspector before any deployment.

## Production connection gate

Do **not** expose this endpoint to the public internet without an authenticated ingress/OAuth 2.1 resource-server boundary. The repository is private and the MCP tools can read source code.

For ChatGPT integration, deploy the bridge behind a stable HTTPS hostname, add OAuth 2.1 (or an equivalent approved authenticated private-MCP ingress), then connect the resulting `https://<host>/mcp` endpoint in ChatGPT Plugins.

The bridge deliberately does not embed API keys, GitHub tokens, signing keys or an owner password. Those stay outside the repository and outside model-visible tool arguments.

## Environment

- `HEARTLINK_WORKSPACE`: absolute or relative path to the checked-out SHOSHI LUNA repository. Defaults to the repository root when run from this tree.

## Next hardening stage

Before enabling writes, implement and runtime-test all of the following:

1. authenticated caller identity;
2. signed owner approval references with expiry and nonce replay protection;
3. snapshot/commit binding for every mutation;
4. exact-path allowlists and bounded patch size;
5. build/test evidence capture;
6. rollback evidence;
7. write/release tools kept disabled until the above gates pass.

Never promote the capability to `VERIFIED_REMOTE` from a model response alone; require a real authenticated remote round trip and evidence ID.
