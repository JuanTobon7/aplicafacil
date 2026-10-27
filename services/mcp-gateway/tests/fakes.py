"""Dobles deterministas: ningún test necesita API keys reales ni red."""

from __future__ import annotations

import json
import uuid
from collections.abc import AsyncIterator
from dataclasses import dataclass, field, replace

import httpx
import httpx2

from aplicafacil_mcp.config import ResilienceSettings
from aplicafacil_mcp.contracts.messages import AiRequestResultEvent, ProviderStatusEvent
from aplicafacil_mcp.domain.errors import AuthenticationError, ConfigNotFoundError
from aplicafacil_mcp.domain.models import (
    ConfigSummary,
    GenerationChunk,
    GenerationRequest,
    GenerationResult,
    HealthStatus,
    ModelInfo,
    ModelParameters,
    ProviderCapabilities,
    ProviderSettings,
    Secret,
    Usage,
)
from aplicafacil_mcp.providers.base import AdapterOptions, AIProvider
from aplicafacil_mcp.providers.factory import ProviderFactory

API_KEY = "sk-test-SECRET-0123456789abcdef"

# Resiliencia sin esperas para los tests
FAST = ResilienceSettings(
    connect_timeout=1, request_timeout=2, retry_base_delay=0, retry_max_delay=0, rate_per_minute=10_000
)


# ── Upstream HTTP compatible con OpenAI (sirve a OpenAI y a OpenRouter) ─────


class FakeUpstream:
    """Escenarios: ok, 400, 401, 403, 404, 429, 500, 503, timeout, connect_error, embedded_error."""

    def __init__(self) -> None:
        self.scenario = "ok"
        self.requests: list[dict] = []

    def _record(self, method: str, path: str, headers, body: bytes) -> None:
        self.requests.append(
            {"method": method, "path": path, "headers": dict(headers), "json": json.loads(body) if body else None}
        )

    def respond(self, method: str, path: str, body: dict | None) -> tuple[int, dict | str, dict]:
        s = self.scenario
        if s == "timeout":
            raise TimeoutError
        if s == "connect_error":
            raise ConnectionError
        if s.isdigit():
            status = int(s)
            # El proveedor repite la key en el mensaje (como hace OpenAI con un 401): no debe filtrarse
            return status, {"error": {"message": f"Upstream says no. key={API_KEY} Bearer {API_KEY}", "code": status}}, (
                {"retry-after": "7"} if status == 429 else {}
            )
        if path.endswith("/key"):
            return 200, {"data": {"label": "test", "usage": 0}}, {}
        if path.endswith("/models"):
            return 200, {
                "object": "list",
                "data": [
                    {"id": "zeta/model-b", "object": "model", "owned_by": "zeta", "created": 0, "context_length": 8000},
                    {"id": "acme/model-a", "object": "model", "owned_by": "acme", "created": 0, "context_length": 128000},
                ],
            }, {}
        if path.endswith("/chat/completions"):
            if s == "embedded_error":
                return 200, {"error": {"message": "upstream provider failed", "code": 502}}, {}
            last = body["messages"][-1]["content"]
            if body.get("stream"):
                return 200, _sse(body["model"], ["Hola", ", ", last]), {"content-type": "text/event-stream"}
            return 200, {
                "id": "cmpl-1",
                "object": "chat.completion",
                "created": 0,
                "model": body["model"],
                "choices": [
                    {"index": 0, "message": {"role": "assistant", "content": f"Hola, {last}"}, "finish_reason": "stop"}
                ],
                "usage": {"prompt_tokens": 5, "completion_tokens": 3, "total_tokens": 8},
            }, {}
        return 404, {"error": {"message": "not found"}}, {}

    def httpx_transport(self) -> httpx.MockTransport:
        def handler(request: httpx.Request) -> httpx.Response:
            self._record(request.method, request.url.path, request.headers, request.content)
            try:
                status, payload, headers = self.respond(request.method, request.url.path, _json(request.content))
            except TimeoutError:
                raise httpx.ReadTimeout("timeout", request=request) from None
            except ConnectionError:
                raise httpx.ConnectError("refused", request=request) from None
            return _response(httpx, status, payload, headers)

        return httpx.MockTransport(handler)

    def httpx2_transport(self) -> httpx2.MockTransport:
        def handler(request: httpx2.Request) -> httpx2.Response:
            self._record(request.method, request.url.path, request.headers, request.content)
            try:
                status, payload, headers = self.respond(request.method, request.url.path, _json(request.content))
            except TimeoutError:
                raise httpx2.ReadTimeout("timeout", request=request) from None
            except ConnectionError:
                raise httpx2.ConnectError("refused", request=request) from None
            return _response(httpx2, status, payload, headers)

        return httpx2.MockTransport(handler)

    def chat_requests(self) -> list[dict]:
        return [r for r in self.requests if r["path"].endswith("/chat/completions")]


def _json(content: bytes):
    return json.loads(content) if content else None


def _response(lib, status: int, payload, headers: dict):
    if isinstance(payload, str):
        return lib.Response(status, content=payload.encode(), headers=headers)
    return lib.Response(status, json=payload, headers=headers)


def _sse(model: str, parts: list[str]) -> str:
    events = [": OPENROUTER PROCESSING\n\n"]
    for i, part in enumerate(parts):
        chunk = {
            "id": "c1",
            "object": "chat.completion.chunk",
            "created": 0,
            "model": model,
            "choices": [{"index": 0, "delta": {"content": part}, "finish_reason": "stop" if i == len(parts) - 1 else None}],
        }
        events.append(f"data: {json.dumps(chunk)}\n\n")
    events.append("data: [DONE]\n\n")
    return "".join(events)


# ── Tercer proveedor, definido SOLO en los tests ────────────────────────────
# Demuestra que agregar un proveedor no exige tocar AIService, el registro ni las tools.


class EchoProviderAdapter(AIProvider):
    provider_type = "ECHO"
    capabilities = ProviderCapabilities(streaming=False, model_listing=True, system_messages=False)

    instances: list[EchoProviderAdapter] = []

    def __init__(self, options: AdapterOptions | None = None) -> None:
        super().__init__(options)
        self.closed_calls = 0
        self.generate_calls = 0
        EchoProviderAdapter.instances.append(self)

    async def _open(self, settings: ProviderSettings) -> None:
        pass

    async def _close(self) -> None:
        self.closed_calls += 1

    def _check_key(self) -> None:
        if self.settings.api_key.reveal().startswith("sk-test-bad"):
            raise AuthenticationError("La API key no es válida o fue revocada.", provider_type=self.provider_type)

    async def _list_models(self) -> list[ModelInfo]:
        self._check_key()
        return [ModelInfo("echo-1"), ModelInfo("echo-2")]

    async def _generate(self, request: GenerationRequest) -> GenerationResult:
        self._check_key()
        self.generate_calls += 1
        params = self.effective_parameters(request)
        return GenerationResult(
            text=f"echo: {request.messages[-1].content}",
            model=self.effective_model(request),
            provider_type=self.provider_type,
            finish_reason="stop",
            usage=Usage(1, 1, 2) if params.max_tokens is None else Usage(1, params.max_tokens, params.max_tokens + 1),
        )

    async def health_check(self) -> HealthStatus:
        try:
            self._ensure_ready()
            self._check_key()
        except Exception as e:
            return HealthStatus(False, 0, getattr(e, "code", "ERROR"), getattr(e, "message", str(e)))
        return HealthStatus(True, 0)


def echo_factory(options: AdapterOptions | None = None) -> ProviderFactory:
    factory = ProviderFactory(options)
    factory.register("ECHO", EchoProviderAdapter)
    return factory


# ── Fuente de configuraciones (ai-config-service) ───────────────────────────


@dataclass
class StoredConfig:
    settings: ProviderSettings
    status: str = "PENDING"


@dataclass
class FakeSource:
    configs: dict[str, StoredConfig] = field(default_factory=dict)
    resolve_calls: list[tuple[str, str]] = field(default_factory=list)

    def add(
        self,
        *,
        user_id: str | None = None,
        provider_type: str = "ECHO",
        model: str = "echo-1",
        api_key: str = API_KEY,
        version: int = 1,
        enabled: bool = True,
        config_id: str | None = None,
        parameters: ModelParameters | None = None,
    ) -> ProviderSettings:
        settings = ProviderSettings(
            config_id=config_id or str(uuid.uuid4()),
            user_id=user_id or str(uuid.uuid4()),
            provider_type=provider_type,
            display_name=f"{provider_type} {version}",
            model=model,
            version=version,
            api_key=Secret(api_key),
            parameters=parameters or ModelParameters(),
            enabled=enabled,
        )
        self.configs[settings.config_id] = StoredConfig(settings)
        return settings

    def update(self, config_id: str, **changes) -> ProviderSettings:
        current = self.configs[config_id].settings
        if "api_key" in changes:
            changes["api_key"] = Secret(changes["api_key"])
        changes.setdefault("version", current.version + 1)
        updated = replace(current, **changes)
        self.configs[config_id].settings = updated
        return updated

    def delete(self, config_id: str) -> None:
        del self.configs[config_id]

    async def list_configs(self, user_id: str | None = None) -> list[ConfigSummary]:
        return [
            ConfigSummary(
                id=s.settings.config_id,
                user_id=s.settings.user_id,
                provider_type=s.settings.provider_type,
                display_name=s.settings.display_name,
                enabled=s.settings.enabled,
                model=s.settings.model,
                version=s.settings.version,
                status=s.status,
            )
            for s in self.configs.values()
            if user_id is None or s.settings.user_id == user_id
        ]

    async def resolve(self, config_id: str, user_id: str) -> ProviderSettings:
        self.resolve_calls.append((config_id, user_id))
        stored = self.configs.get(config_id)
        if stored is None or stored.settings.user_id != user_id:
            raise ConfigNotFoundError("La configuración no existe")
        return stored.settings


class RecordingPublisher:
    def __init__(self) -> None:
        self.statuses: list[ProviderStatusEvent] = []
        self.results: list[AiRequestResultEvent] = []

    async def publish_status(self, event: ProviderStatusEvent) -> None:
        self.statuses.append(event)

    async def publish_result(self, event: AiRequestResultEvent) -> None:
        self.results.append(event)


async def collect(stream: AsyncIterator[GenerationChunk]) -> list[GenerationChunk]:
    return [chunk async for chunk in stream]
