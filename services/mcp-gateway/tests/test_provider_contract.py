"""Suite de contrato de ``AIProvider`` (providers/base.py): la MISMA batería para cada adaptador.

Si un adaptador nuevo pasa esta suite, puede reemplazar a cualquier otro sin cambiar la orquestación (LSP).
"""

from __future__ import annotations

import uuid
from collections.abc import Callable
from dataclasses import dataclass

import pytest
from fakes import API_KEY, EchoProviderAdapter, FakeUpstream, collect

from aplicafacil_mcp.domain.errors import (
    AuthenticationError,
    ConfigurationError,
    InvalidRequestError,
    ModelNotFoundError,
    ProviderError,
    ProviderNotInitializedError,
    ProviderTimeoutError,
    ProviderUnavailableError,
    RateLimitError,
    UnsupportedCapabilityError,
)
from aplicafacil_mcp.domain.models import (
    ChatMessage,
    GenerationRequest,
    ModelParameters,
    ProviderSettings,
    Secret,
)
from aplicafacil_mcp.providers.base import AdapterOptions, AIProvider
from aplicafacil_mcp.providers.openai_adapter import OpenAIProviderAdapter
from aplicafacil_mcp.providers.openrouter_adapter import OpenRouterProviderAdapter

OPTIONS = AdapterOptions(connect_timeout=1, request_timeout=2)


@dataclass
class Harness:
    provider_type: str
    model: str
    make: Callable[[FakeUpstream], AIProvider]
    http: bool = True

    def settings(self, **overrides) -> ProviderSettings:
        values = dict(
            config_id=str(uuid.uuid4()),
            user_id=str(uuid.uuid4()),
            provider_type=self.provider_type,
            display_name="test",
            model=self.model,
            version=1,
            api_key=Secret(API_KEY),
            parameters=ModelParameters(temperature=0.3, max_tokens=64),
        )
        values.update(overrides)
        return ProviderSettings(**values)


OPENAI = Harness("OPENAI", "gpt-test", lambda up: OpenAIProviderAdapter(OPTIONS, transport=up.httpx2_transport()))
OPENROUTER = Harness(
    "OPENROUTER", "acme/model-a", lambda up: OpenRouterProviderAdapter(OPTIONS, transport=up.httpx_transport())
)
ECHO = Harness("ECHO", "echo-1", lambda up: EchoProviderAdapter(OPTIONS), http=False)

ALL = [OPENAI, OPENROUTER, ECHO]
HTTP = [OPENAI, OPENROUTER]


def ids(h: Harness) -> str:
    return h.provider_type


async def ready(harness: Harness, upstream: FakeUpstream, **overrides) -> AIProvider:
    provider = harness.make(upstream)
    await provider.initialize(harness.settings(**overrides))
    provider.validate_configuration()
    return provider


def request(text: str = "mundo", **kwargs) -> GenerationRequest:
    return GenerationRequest((ChatMessage("user", text),), **kwargs)


# ── 1. Ciclo de vida ───────────────────────────────────────────────────────


@pytest.mark.parametrize("harness", ALL, ids=ids)
async def test_operations_require_initialize(harness, upstream):
    provider = harness.make(upstream)
    with pytest.raises(ProviderNotInitializedError):
        await provider.generate_response(request())
    with pytest.raises(ProviderNotInitializedError):
        await provider.list_models()
    assert (await provider.health_check()).healthy is False


@pytest.mark.parametrize("harness", ALL, ids=ids)
async def test_close_is_idempotent_and_final(harness, upstream):
    provider = await ready(harness, upstream)
    await provider.close()
    await provider.close()
    with pytest.raises(ProviderNotInitializedError):
        await provider.generate_response(request())
    assert provider.is_ready is False


@pytest.mark.parametrize("harness", ALL, ids=ids)
async def test_initialize_twice_or_with_foreign_settings_fails(harness, upstream):
    provider = await ready(harness, upstream)
    with pytest.raises(ConfigurationError):
        await provider.initialize(harness.settings())
    other = harness.make(upstream)
    with pytest.raises(ConfigurationError):
        await other.initialize(harness.settings(provider_type="SOMETHING_ELSE"))


# ── 2. Inicializar y validar no llaman a la red ─────────────────────────────


@pytest.mark.parametrize("harness", ALL, ids=ids)
async def test_initialize_and_validate_do_not_touch_the_network(harness, upstream):
    await ready(harness, upstream)
    assert upstream.requests == []


@pytest.mark.parametrize("harness", ALL, ids=ids)
async def test_empty_model_is_a_configuration_error(harness, upstream):
    provider = harness.make(upstream)
    await provider.initialize(harness.settings(model=" "))
    with pytest.raises(ConfigurationError):
        provider.validate_configuration()


@pytest.mark.parametrize("harness", ALL, ids=ids)
async def test_base_url_is_rejected_unless_supported_and_safe(harness, upstream):
    provider = harness.make(upstream)
    await provider.initialize(harness.settings(base_url="http://10.0.0.8:11434/v1"))
    with pytest.raises(ConfigurationError):
        provider.validate_configuration()


# ── 5. Respuesta normalizada ────────────────────────────────────────────────


@pytest.mark.parametrize("harness", ALL, ids=ids)
async def test_generate_returns_a_normalized_result(harness, upstream):
    provider = await ready(harness, upstream)
    result = await provider.generate_response(request("mundo"))
    assert isinstance(result.text, str) and "mundo" in result.text
    assert result.model == harness.model
    assert result.provider_type == harness.provider_type
    assert result.finish_reason == "stop"
    assert result.usage is not None and result.usage.total_tokens is not None


@pytest.mark.parametrize("harness", HTTP, ids=ids)
async def test_request_overrides_merge_with_configured_defaults(harness, upstream):
    provider = await ready(harness, upstream)
    await provider.generate_response(request(parameters=ModelParameters(temperature=1.1), model=harness.model))
    body = upstream.chat_requests()[-1]["json"]
    assert body["temperature"] == 1.1  # override
    assert 64 in (body.get("max_tokens"), body.get("max_completion_tokens"))  # valor guardado
    assert body["messages"] == [{"role": "user", "content": "mundo"}]


@pytest.mark.parametrize("harness", ALL, ids=ids)
async def test_list_models_is_sorted_model_info(harness, upstream):
    provider = await ready(harness, upstream)
    models = await provider.list_models()
    assert models and [m.id for m in models] == sorted(m.id for m in models)


# ── 3. Semántica de errores ────────────────────────────────────────────────

ERROR_TABLE = [
    ("401", AuthenticationError),
    ("403", AuthenticationError),
    ("429", RateLimitError),
    ("404", ModelNotFoundError),
    ("400", InvalidRequestError),
    ("500", ProviderUnavailableError),
    ("503", ProviderUnavailableError),
    ("timeout", ProviderTimeoutError),
    ("connect_error", ProviderUnavailableError),
]


@pytest.mark.parametrize("harness", HTTP, ids=ids)
@pytest.mark.parametrize(("scenario", "expected"), ERROR_TABLE, ids=[s for s, _ in ERROR_TABLE])
async def test_errors_are_translated_and_never_leak_the_key(harness, upstream, scenario, expected):
    provider = await ready(harness, upstream)
    upstream.scenario = scenario
    with pytest.raises(expected) as info:
        await provider.generate_response(request())
    error = info.value
    assert isinstance(error, ProviderError)
    assert error.provider_type == harness.provider_type
    assert API_KEY not in str(error) and API_KEY not in repr(error.args)
    assert error.retryable == (expected in (RateLimitError, ProviderUnavailableError, ProviderTimeoutError))


@pytest.mark.parametrize("harness", HTTP, ids=ids)
async def test_rate_limit_carries_retry_after(harness, upstream):
    provider = await ready(harness, upstream)
    upstream.scenario = "429"
    with pytest.raises(RateLimitError) as info:
        await provider.generate_response(request())
    assert info.value.retry_after == 7


# ── 6. health_check informa, no lanza ──────────────────────────────────────


@pytest.mark.parametrize("harness", HTTP, ids=ids)
@pytest.mark.parametrize("scenario", ["401", "503", "timeout"])
async def test_health_check_reports_instead_of_raising(harness, upstream, scenario):
    provider = await ready(harness, upstream)
    assert (await provider.health_check()).healthy is True
    upstream.scenario = scenario
    status = await provider.health_check()
    assert status.healthy is False
    assert status.error_code in {"AUTHENTICATION_FAILED", "PROVIDER_UNAVAILABLE", "TIMEOUT"}
    assert API_KEY not in (status.message or "")


async def test_echo_health_check_reports_bad_key():
    provider = await ready(ECHO, FakeUpstream(), api_key=Secret("sk-test-bad-123"))
    status = await provider.health_check()
    assert (status.healthy, status.error_code) == (False, "AUTHENTICATION_FAILED")


# ── 4. Capacidades explícitas ──────────────────────────────────────────────


@pytest.mark.parametrize("harness", ALL, ids=ids)
async def test_streaming_matches_declared_capability(harness, upstream):
    provider = await ready(harness, upstream)
    if provider.capabilities.streaming:
        chunks = await collect(provider.stream_response(request("mundo")))
        assert "".join(c.delta for c in chunks) == "Hola, mundo"
        assert chunks[-1].finish_reason == "stop"
    else:
        with pytest.raises(UnsupportedCapabilityError):
            await collect(provider.stream_response(request()))


@pytest.mark.parametrize("harness", ALL, ids=ids)
async def test_system_messages_follow_declared_capability(harness, upstream):
    provider = await ready(harness, upstream)
    with_system = GenerationRequest((ChatMessage("system", "breve"), ChatMessage("user", "hola")))
    if provider.capabilities.system_messages:
        assert (await provider.generate_response(with_system)).text
    else:
        with pytest.raises(UnsupportedCapabilityError):
            await provider.generate_response(with_system)


@pytest.mark.parametrize("harness", HTTP, ids=ids)
async def test_streaming_errors_are_translated(harness, upstream):
    provider = await ready(harness, upstream)
    upstream.scenario = "401"
    with pytest.raises(AuthenticationError):
        await collect(provider.stream_response(request()))


# ── 7. Nada imprime la key ─────────────────────────────────────────────────


@pytest.mark.parametrize("harness", ALL, ids=ids)
async def test_repr_never_contains_the_key(harness, upstream):
    provider = await ready(harness, upstream)
    assert API_KEY not in repr(provider)
    assert API_KEY not in repr(provider.settings)
    assert API_KEY not in str(provider.settings.api_key)


# ── Particularidades que cada adaptador absorbe ────────────────────────────


async def test_openrouter_validates_the_key_with_the_key_endpoint(upstream):
    provider = await ready(OPENROUTER, upstream)
    await provider.health_check()
    assert upstream.requests[-1]["path"].endswith("/key")
    assert upstream.requests[-1]["headers"]["authorization"] == f"Bearer {API_KEY}"


async def test_openrouter_requires_vendor_prefixed_model(upstream):
    provider = OPENROUTER.make(upstream)
    await provider.initialize(OPENROUTER.settings(model="gpt-4o"))
    with pytest.raises(ConfigurationError):
        provider.validate_configuration()


async def test_openrouter_embedded_error_in_200_is_an_error(upstream):
    provider = await ready(OPENROUTER, upstream)
    upstream.scenario = "embedded_error"
    with pytest.raises(ProviderUnavailableError):
        await provider.generate_response(request())


async def test_openai_compatible_base_url_uses_max_tokens(upstream):
    provider = await ready(OPENAI, upstream, base_url="http://127.0.0.1:11434/v1")
    await provider.generate_response(request())
    body = upstream.chat_requests()[-1]["json"]
    assert body["max_tokens"] == 64 and "max_completion_tokens" not in body


async def test_openai_official_api_uses_max_completion_tokens(upstream):
    provider = await ready(OPENAI, upstream)
    await provider.generate_response(request())
    body = upstream.chat_requests()[-1]["json"]
    assert body["max_completion_tokens"] == 64 and "max_tokens" not in body
