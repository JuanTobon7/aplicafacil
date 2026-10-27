"""El servicio completo por HTTP real (uvicorn): Django (health/ready/métricas) + MCP Streamable HTTP con JWT.

Los JWT se firman con una llave RSA generada en el test, igual que los emite identity-service (RS256,
``aud = aplicafacil-api``, ``roles``).
"""

from __future__ import annotations

import asyncio
import socket
import time
import uuid

import httpx
import httpx2
import jwt
import pytest
import uvicorn
from cryptography.hazmat.primitives.asymmetric import rsa
from fakes import FAST, echo_factory
from mcp import Client
from mcp.client.streamable_http import streamable_http_client

from aplicafacil_mcp.asgi import create_application
from aplicafacil_mcp.config import Settings
from aplicafacil_mcp.container import Container
from aplicafacil_mcp.infrastructure.jwt_verifier import IdentityTokenVerifier

ISSUER = "http://127.0.0.1:9000"
AUDIENCE = "aplicafacil-api"


class StaticJwks:
    """Sustituye a PyJWKClient: devuelve la llave pública del test."""

    def __init__(self, private_key) -> None:
        self._key = jwt.PyJWK.from_dict(
            {**jwt.algorithms.RSAAlgorithm.to_jwk(private_key.public_key(), as_dict=True), "kid": "k1", "alg": "RS256"}
        )

    def get_signing_key_from_jwt(self, token: str):
        return self._key


@pytest.fixture(scope="module")
def private_key():
    return rsa.generate_private_key(public_exponent=65537, key_size=2048)


def token(private_key, *, sub="ana", roles=("USER",), aud=AUDIENCE, iss=ISSUER, ttl=900) -> str:
    now = int(time.time())
    claims = {"sub": sub, "roles": list(roles), "aud": [aud], "iss": iss, "iat": now, "exp": now + ttl, "jti": str(uuid.uuid4())}
    return jwt.encode(claims, private_key, algorithm="RS256", headers={"kid": "k1"})


def free_port() -> int:
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


@pytest.fixture
async def running(private_key, source):
    port = free_port()
    settings = Settings(
        port=port,
        resource_url=f"http://127.0.0.1:{port}/mcp",
        messaging_enabled=False,
        resilience=FAST,
        log_format="text",
    )
    container = Container(
        settings,
        source=source,
        factory=echo_factory(),
        token_verifier=IdentityTokenVerifier("unused", ISSUER, AUDIENCE, jwks_client=StaticJwks(private_key)),
    )
    app = create_application(settings, container)
    server = uvicorn.Server(uvicorn.Config(app, host="127.0.0.1", port=port, lifespan="on", log_config=None))
    task = asyncio.create_task(server.serve())
    while not server.started:
        await asyncio.sleep(0.02)
    yield f"http://127.0.0.1:{port}", container
    server.should_exit = True
    await task


async def test_health_readiness_and_metrics(running):
    base, _ = running
    async with httpx.AsyncClient(base_url=base) as http:
        assert (await http.get("/health/live")).json() == {"status": "UP"}
        for _ in range(50):
            ready = await http.get("/health/ready")
            if ready.status_code == 200:
                break
            await asyncio.sleep(0.05)
        assert ready.status_code == 200 and ready.json()["checks"]["backend"] is True
        metrics = await http.get("/metrics")
        assert "mcp_reconciliations_total" in metrics.text
        assert (await http.get("/")).json()["mcp"] == "/mcp"


async def test_mcp_requires_a_valid_user_token(running, private_key):
    base, _ = running
    init = {
        "jsonrpc": "2.0",
        "id": 1,
        "method": "initialize",
        "params": {"protocolVersion": "2025-06-18", "capabilities": {}, "clientInfo": {"name": "t", "version": "1"}},
    }
    headers = {"Accept": "application/json, text/event-stream", "Content-Type": "application/json"}
    async with httpx.AsyncClient(base_url=base) as http:
        anonymous = await http.post("/mcp", json=init, headers=headers)
        assert anonymous.status_code == 401
        assert "resource_metadata" in anonymous.headers["www-authenticate"]

        metadata = (await http.get("/.well-known/oauth-protected-resource/mcp")).json()
        assert metadata["authorization_servers"] == [ISSUER + "/"] or metadata["authorization_servers"] == [ISSUER]

        for bad in (
            token(private_key, roles=("SERVICE",)),  # tokens de servicio no
            token(private_key, aud="otra-api"),
            token(private_key, iss="http://evil"),
            token(private_key, ttl=-120),
            "no-es-un-jwt",
        ):
            response = await http.post("/mcp", json=init, headers={**headers, "Authorization": f"Bearer {bad}"})
            assert response.status_code == 401, bad


async def test_tool_call_over_http_is_scoped_to_the_token_subject(running, private_key, source):
    base, _ = running
    mine = source.add(user_id="ana")
    others = source.add(user_id="beto")
    http_client = httpx2.AsyncClient(headers={"Authorization": f"Bearer {token(private_key, sub='ana')}"}, timeout=10)
    async with Client(streamable_http_client(f"{base}/mcp", http_client=http_client)) as client:
        listed = await client.call_tool("list_available_providers", {})
        assert [p["config_id"] for p in listed.structured_content["providers"]] == [mine.config_id]
        ok = await client.call_tool("generate_ai_response", {"config_id": mine.config_id, "prompt": "hola"})
        assert ok.structured_content["text"] == "echo: hola"
        denied = await client.call_tool("generate_ai_response", {"config_id": others.config_id, "prompt": "x"})
        assert denied.is_error and "CONFIG_NOT_FOUND" in denied.content[0].text


async def test_dns_rebinding_protection(running, private_key):
    base, _ = running
    async with httpx.AsyncClient(base_url=base) as http:
        response = await http.post(
            "/mcp",
            json={"jsonrpc": "2.0", "id": 1, "method": "ping"},
            headers={
                "Authorization": f"Bearer {token(private_key)}",
                "Accept": "application/json, text/event-stream",
                "Host": "evil.example:8000",
            },
        )
    assert response.status_code in (400, 403, 421)
