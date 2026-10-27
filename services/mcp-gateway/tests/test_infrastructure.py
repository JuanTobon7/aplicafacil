"""Eureka (API REST) y cliente de ai-config-service, contra servidores simulados con httpx.MockTransport."""

from __future__ import annotations

import json

import httpx
import pytest
from fakes import API_KEY

from aplicafacil_mcp.config import EurekaSettings
from aplicafacil_mcp.domain.errors import BackendUnavailableError, ConfigNotFoundError
from aplicafacil_mcp.infrastructure.backend_client import AiConfigClient, ServiceTokenProvider
from aplicafacil_mcp.infrastructure.eureka import EurekaClient

# ── Eureka ─────────────────────────────────────────────────────────────────


class FakeEureka:
    def __init__(self) -> None:
        self.calls: list[tuple[str, str]] = []
        self.registered: dict | None = None
        self.forget_next_heartbeat = False

    def handler(self, request: httpx.Request) -> httpx.Response:
        path = request.url.path
        self.calls.append((request.method, path))
        if request.method == "POST" and path == "/eureka/apps/MCP-GATEWAY":
            self.registered = json.loads(request.content)
            return httpx.Response(204)
        if request.method == "PUT":
            if self.forget_next_heartbeat:
                self.forget_next_heartbeat = False
                return httpx.Response(404)
            return httpx.Response(200)
        if request.method == "DELETE":
            return httpx.Response(200)
        if request.method == "GET" and path == "/eureka/apps/AI-CONFIG-SERVICE":
            return httpx.Response(
                200,
                json={
                    "application": {
                        "instance": [
                            {"ipAddr": "10.1.0.7", "hostName": "ai-1", "status": "UP", "port": {"$": 8086}},
                            {"ipAddr": "10.1.0.8", "hostName": "ai-2", "status": "DOWN", "port": {"$": 8086}},
                        ]
                    }
                },
            )
        return httpx.Response(404)


@pytest.fixture
def eureka():
    fake = FakeEureka()
    settings = EurekaSettings(
        enabled=True, url="http://eureka:8761/eureka/", instance_ip="10.1.0.5", instance_hostname="mcp-container-a1b2"
    )
    client = EurekaClient(settings, httpx.AsyncClient(transport=httpx.MockTransport(fake.handler)), port=8000, instance_id="h:1:abc123")
    return fake, client


async def test_registers_with_a_reachable_address_and_health_urls(eureka):
    fake, client = eureka
    assert await client.register()
    instance = fake.registered["instance"]
    assert instance["app"] == "MCP-GATEWAY"
    assert instance["ipAddr"] == "10.1.0.5"
    assert instance["hostName"] == "10.1.0.5"  # prefer-ip-address: no se anuncia el id del contenedor
    assert instance["healthCheckUrl"] == "http://10.1.0.5:8000/health/ready"
    assert instance["port"] == {"$": 8000, "@enabled": "true"}
    assert instance["leaseInfo"] == {"renewalIntervalInSecs": 30, "durationInSecs": 90}


async def test_heartbeat_reregisters_when_eureka_forgot_the_instance(eureka):
    fake, client = eureka
    await client.register()
    fake.forget_next_heartbeat = True
    assert await client.heartbeat()
    assert [m for m, _ in fake.calls] == ["POST", "PUT", "POST"]


async def test_deregisters_on_shutdown(eureka):
    fake, client = eureka
    await client.register()
    await client.deregister()
    assert fake.calls[-1] == ("DELETE", f"/eureka/apps/MCP-GATEWAY/{client.instance_id}")
    assert client.registered is False


async def test_resolves_lb_urls_to_up_instances(eureka):
    _, client = eureka
    assert await client.resolve("lb://ai-config-service") == "http://10.1.0.7:8086"
    assert await client.resolve("http://127.0.0.1:8086") == "http://127.0.0.1:8086"


# ── ai-config-service ──────────────────────────────────────────────────────

CONFIG = {
    "id": "c1",
    "userId": "u1",
    "providerType": "OPENAI",
    "displayName": "Mi OpenAI",
    "enabled": True,
    "model": "gpt-4.1-mini",
    "baseUrl": None,
    "parameters": {"temperature": 0.2, "maxTokens": 100, "topP": None},
    "version": 3,
    "apiKey": API_KEY,
}


class FakeBackend:
    def __init__(self) -> None:
        self.tokens_issued = 0
        self.statuses: list[int] = []  # respuestas a forzar en orden
        self.seen_auth: list[str] = []

    def handler(self, request: httpx.Request) -> httpx.Response:
        if request.url.path == "/internal/token":
            assert request.headers["authorization"].startswith("Basic ")
            self.tokens_issued += 1
            return httpx.Response(200, json={"accessToken": f"tok-{self.tokens_issued}", "expiresAt": "2099-01-01T00:00:00Z"})
        self.seen_auth.append(request.headers.get("authorization", ""))
        if self.statuses:
            return httpx.Response(self.statuses.pop(0))
        if request.url.path == "/internal/provider-configs/c1":
            if request.url.params.get("userId") != "u1":
                return httpx.Response(404)
            return httpx.Response(200, json=dict(CONFIG))
        if request.url.path == "/internal/provider-configs":
            summary = {k: v for k, v in CONFIG.items() if k != "apiKey"} | {"status": "ACTIVE", "availableModels": ["a"]}
            return httpx.Response(200, json=[summary])
        return httpx.Response(404)


@pytest.fixture
def backend():
    fake = FakeBackend()
    http = httpx.AsyncClient(transport=httpx.MockTransport(fake.handler))
    tokens = ServiceTokenProvider(http, "http://identity", "mcp-gateway", "s3cr3t")
    return fake, AiConfigClient(http, "http://ai-config", tokens, attempts=3)


async def test_resolve_returns_settings_with_a_wrapped_secret(backend):
    fake, client = backend
    settings = await client.resolve("c1", "u1")
    assert settings.api_key.reveal() == API_KEY
    assert API_KEY not in repr(settings)
    assert settings.parameters.max_tokens == 100 and settings.version == 3
    assert fake.seen_auth == ["Bearer tok-1"]


async def test_ownership_is_enforced_by_the_backend(backend):
    _, client = backend
    with pytest.raises(ConfigNotFoundError):
        await client.resolve("c1", "otro")


async def test_service_token_is_cached_and_refreshed_after_401(backend):
    fake, client = backend
    await client.list_configs()
    await client.list_configs()
    assert fake.tokens_issued == 1
    fake.statuses = [401]
    await client.list_configs()
    assert fake.tokens_issued == 2


async def test_transient_errors_are_retried_then_reported(backend):
    fake, client = backend
    fake.statuses = [503, 502]
    assert (await client.list_configs())[0].available_models == ("a",)
    fake.statuses = [500, 500, 500]
    with pytest.raises(BackendUnavailableError):
        await client.list_configs()


async def test_lb_urls_are_resolved_per_call():
    fake = FakeBackend()
    http = httpx.AsyncClient(transport=httpx.MockTransport(fake.handler))
    resolved: list[str] = []

    async def resolve(url: str) -> str:
        resolved.append(url)
        return "http://10.1.0.7:8086"

    client = AiConfigClient(http, "lb://ai-config-service", ServiceTokenProvider(http, "http://i", "m", "s"), resolve_url=resolve)
    await client.list_configs("u1")
    assert resolved == ["lb://ai-config-service"]
