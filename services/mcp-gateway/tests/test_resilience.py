"""ResilientProvider: reintentos solo donde es seguro, circuit breaker, límites y timeouts."""

from __future__ import annotations

import asyncio
import uuid

import pytest
from fakes import API_KEY, FAST

from aplicafacil_mcp.config import ResilienceSettings
from aplicafacil_mcp.domain.errors import (
    AuthenticationError,
    CircuitOpenError,
    ConcurrencyLimitError,
    ProviderTimeoutError,
    ProviderUnavailableError,
    RateLimitError,
)
from aplicafacil_mcp.domain.models import (
    ChatMessage,
    GenerationRequest,
    GenerationResult,
    HealthStatus,
    ModelInfo,
    ProviderCapabilities,
    ProviderSettings,
    Secret,
)
from aplicafacil_mcp.providers.base import AIProvider
from aplicafacil_mcp.providers.resilience import CircuitBreaker, ResilientProvider, RetryPolicy, TokenBucket


class Scripted(AIProvider):
    """Adaptador que falla según un guion."""

    provider_type = "SCRIPTED"
    capabilities = ProviderCapabilities(streaming=False, model_listing=True)

    def __init__(self, errors: list[Exception | None], delay: float = 0) -> None:
        super().__init__()
        self.errors = errors
        self.calls = 0
        self.delay = delay

    async def _open(self, settings): ...

    async def _close(self): ...

    async def _next(self):
        self.calls += 1
        if self.delay:
            await asyncio.sleep(self.delay)
        error = self.errors.pop(0) if self.errors else None
        if error:
            raise error

    async def _list_models(self):
        await self._next()
        return [ModelInfo("m")]

    async def _generate(self, request):
        await self._next()
        return GenerationResult("ok", "m", self.provider_type)

    async def health_check(self):
        return HealthStatus(True, 0)


def settings() -> ProviderSettings:
    return ProviderSettings(str(uuid.uuid4()), "u", "SCRIPTED", "x", "m", 1, Secret(API_KEY))


async def wrap(inner: Scripted, config: ResilienceSettings = FAST) -> ResilientProvider:
    sleeps: list[float] = []

    async def fake_sleep(seconds: float) -> None:
        sleeps.append(seconds)

    provider = ResilientProvider(inner, config, sleep=fake_sleep)
    provider.sleeps = sleeps
    await provider.initialize(settings())
    return provider


REQUEST = GenerationRequest((ChatMessage("user", "hola"),))


def unavailable() -> ProviderUnavailableError:
    return ProviderUnavailableError("down", provider_type="SCRIPTED")


async def test_idempotent_operations_retry_retryable_errors():
    inner = Scripted([unavailable(), unavailable()])
    provider = await wrap(inner)
    assert await provider.list_models()
    assert inner.calls == 3
    assert len(provider.sleeps) == 2


async def test_generation_is_not_retried_by_default():
    inner = Scripted([unavailable()])
    provider = await wrap(inner)
    with pytest.raises(ProviderUnavailableError):
        await provider.generate_response(REQUEST)
    assert inner.calls == 1


async def test_generation_retry_requires_explicit_policy():
    inner = Scripted([unavailable()])
    provider = await wrap(inner, ResilienceSettings(**{**FAST.__dict__, "retry_generation": True}))
    result = await provider.generate_response(REQUEST)
    assert result.text == "ok" and inner.calls == 2
    assert result.latency_ms is not None


async def test_non_retryable_errors_fail_fast():
    inner = Scripted([AuthenticationError("no", provider_type="SCRIPTED")])
    provider = await wrap(inner)
    with pytest.raises(AuthenticationError):
        await provider.list_models()
    assert inner.calls == 1


async def test_retry_after_is_respected():
    policy = RetryPolicy(max_attempts=3, base_delay=0.5, max_delay=10)
    assert policy.delay(1, RateLimitError("x", retry_after=4)) == 4
    assert 0 <= policy.delay(3, unavailable()) <= 2.0


async def test_circuit_opens_after_consecutive_failures_and_recovers():
    now = [0.0]
    breaker = CircuitBreaker(failure_threshold=2, reset_seconds=30, clock=lambda: now[0])
    breaker.on_failure(unavailable())
    assert breaker.state == "CLOSED"
    assert breaker.on_failure(unavailable()) is True
    with pytest.raises(CircuitOpenError):
        breaker.before_call("X")
    now[0] = 31
    breaker.before_call("X")  # HALF_OPEN: una prueba
    with pytest.raises(CircuitOpenError):
        breaker.before_call("X")  # solo una a la vez
    breaker.on_success()
    assert breaker.state == "CLOSED"


async def test_auth_errors_do_not_open_the_circuit():
    breaker = CircuitBreaker(failure_threshold=1, reset_seconds=30)
    assert breaker.on_failure(AuthenticationError("no")) is False
    assert breaker.state == "CLOSED"


async def test_open_circuit_rejects_without_calling_the_provider():
    config = ResilienceSettings(**{**FAST.__dict__, "breaker_failure_threshold": 2, "retry_max_attempts": 1})
    inner = Scripted([unavailable(), unavailable()])
    provider = await wrap(inner, config)
    for _ in range(2):
        with pytest.raises(ProviderUnavailableError):
            await provider.generate_response(REQUEST)
    with pytest.raises(CircuitOpenError):
        await provider.generate_response(REQUEST)
    assert inner.calls == 2


async def test_timeouts_become_provider_timeout_errors():
    config = ResilienceSettings(**{**FAST.__dict__, "request_timeout": 0.05})
    provider = await wrap(Scripted([], delay=1), config)
    with pytest.raises(ProviderTimeoutError):
        await provider.generate_response(REQUEST)


async def test_local_rate_limit():
    bucket = TokenBucket(rate_per_minute=2, clock=lambda: 0.0)
    assert bucket.try_acquire() is None
    assert bucket.try_acquire() is None
    assert bucket.try_acquire() == pytest.approx(30.0)


async def test_concurrency_limit_is_bounded():
    # Un lugar; cada llamada tarda 0.15 s y la espera por un lugar se corta a los 0.2 s:
    # la primera y la segunda terminan, la tercera no consigue lugar a tiempo
    config = ResilienceSettings(**{**FAST.__dict__, "max_concurrency": 1, "request_timeout": 0.2})
    provider = await wrap(Scripted([], delay=0.15), config)
    results = await asyncio.gather(*(provider.generate_response(REQUEST) for _ in range(3)), return_exceptions=True)
    assert sum(isinstance(r, GenerationResult) for r in results) == 2
    assert sum(isinstance(r, ConcurrencyLimitError) for r in results) == 1


async def test_resilient_provider_keeps_the_contract():
    inner = Scripted([])
    provider = await wrap(inner)
    assert provider.provider_type == "SCRIPTED"
    assert provider.capabilities is inner.capabilities
    await provider.close()
    assert provider.is_ready is False
