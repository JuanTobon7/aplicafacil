"""``ResilientProvider``: decorador de ``AIProvider`` con timeouts, reintentos, circuit breaker y límites.

Es a su vez un ``AIProvider`` que respeta el mismo contrato (puede reemplazar al adaptador que envuelve), así que
la orquestación no sabe si habla con un adaptador desnudo o protegido.

Reintentos: solo errores ``retryable`` y solo en operaciones idempotentes (``list_models``, ``health_check``).
``generate_response`` y ``stream_response`` no se reintentan salvo ``retry_generation=True`` (política explícita):
repetir una generación cuesta dinero y puede duplicar efectos.
"""

from __future__ import annotations

import asyncio
import random
import time
from collections.abc import AsyncIterator, Awaitable, Callable
from dataclasses import dataclass
from typing import TypeVar

from aplicafacil_mcp import metrics
from aplicafacil_mcp.config import ResilienceSettings
from aplicafacil_mcp.domain.errors import (
    CircuitOpenError,
    ConcurrencyLimitError,
    ProviderError,
    ProviderTimeoutError,
    ProviderUnavailableError,
    RateLimitError,
)
from aplicafacil_mcp.domain.models import (
    GenerationChunk,
    GenerationRequest,
    GenerationResult,
    HealthStatus,
    ModelInfo,
    ProviderSettings,
)
from aplicafacil_mcp.providers.base import AIProvider

T = TypeVar("T")
Clock = Callable[[], float]


class CircuitBreaker:
    """CLOSED → (N fallos de disponibilidad seguidos) → OPEN → (reset_seconds) → HALF_OPEN → 1 prueba."""

    def __init__(self, failure_threshold: int, reset_seconds: float, clock: Clock = time.monotonic) -> None:
        self.failure_threshold = failure_threshold
        self.reset_seconds = reset_seconds
        self._clock = clock
        self._failures = 0
        self._opened_at: float | None = None
        self._half_open_trial = False

    @property
    def state(self) -> str:
        if self._opened_at is None:
            return "CLOSED"
        if self._clock() - self._opened_at >= self.reset_seconds:
            return "HALF_OPEN"
        return "OPEN"

    def before_call(self, provider_type: str) -> None:
        state = self.state
        if state == "OPEN" or (state == "HALF_OPEN" and self._half_open_trial):
            raise CircuitOpenError(
                "El proveedor falló varias veces seguidas; se reintentará en unos segundos", provider_type=provider_type
            )
        if state == "HALF_OPEN":
            self._half_open_trial = True

    def on_success(self) -> None:
        self._failures = 0
        self._opened_at = None
        self._half_open_trial = False

    def on_failure(self, error: ProviderError) -> bool:
        """Solo cuentan los errores de disponibilidad. @return True si el circuito se abrió."""
        if not isinstance(error, (ProviderUnavailableError, ProviderTimeoutError)):
            self._half_open_trial = False
            return False
        self._failures += 1
        if self._half_open_trial or self._failures >= self.failure_threshold:
            self._opened_at = self._clock()
            self._half_open_trial = False
            return True
        return False


class TokenBucket:
    """Límite local de solicitudes por minuto por configuración (protege la cuota de la persona)."""

    def __init__(self, rate_per_minute: int, clock: Clock = time.monotonic) -> None:
        self.capacity = max(1, rate_per_minute)
        self._tokens = float(self.capacity)
        self._rate = self.capacity / 60.0
        self._clock = clock
        self._updated = clock()

    def try_acquire(self) -> float | None:
        """@return None si hay token; si no, los segundos hasta el próximo."""
        now = self._clock()
        self._tokens = min(self.capacity, self._tokens + (now - self._updated) * self._rate)
        self._updated = now
        if self._tokens >= 1:
            self._tokens -= 1
            return None
        return (1 - self._tokens) / self._rate


@dataclass(frozen=True)
class RetryPolicy:
    max_attempts: int = 3
    base_delay: float = 0.5
    max_delay: float = 8.0

    def delay(self, attempt: int, error: ProviderError) -> float:
        """Exponencial con jitter completo; respeta ``Retry-After`` del proveedor."""
        if isinstance(error, RateLimitError) and error.retry_after:
            return min(error.retry_after, self.max_delay)
        return random.uniform(0, min(self.max_delay, self.base_delay * 2 ** (attempt - 1)))


class ResilientProvider(AIProvider):
    def __init__(
        self,
        inner: AIProvider,
        settings: ResilienceSettings,
        *,
        clock: Clock = time.monotonic,
        sleep: Callable[[float], Awaitable[None]] = asyncio.sleep,
    ) -> None:
        super().__init__(inner.options)
        self.inner = inner
        self.provider_type = inner.provider_type
        self.capabilities = inner.capabilities
        self._config = settings
        self._retry = RetryPolicy(settings.retry_max_attempts, settings.retry_base_delay, settings.retry_max_delay)
        self.breaker = CircuitBreaker(settings.breaker_failure_threshold, settings.breaker_reset_seconds, clock)
        self._bucket = TokenBucket(settings.rate_per_minute, clock)
        self._semaphore = asyncio.Semaphore(settings.max_concurrency)
        self._sleep = sleep

    # El ciclo de vida se delega tal cual

    async def initialize(self, settings: ProviderSettings) -> None:
        await self.inner.initialize(settings)

    def validate_configuration(self) -> None:
        self.inner.validate_configuration()

    async def close(self) -> None:
        await self.inner.close()

    @property
    def settings(self) -> ProviderSettings:
        return self.inner.settings

    @property
    def is_ready(self) -> bool:
        return self.inner.is_ready

    # Operaciones protegidas

    async def list_models(self) -> list[ModelInfo]:
        return await self._call("list_models", self.inner.list_models, retry=True)

    async def generate_response(self, request: GenerationRequest) -> GenerationResult:
        started = time.perf_counter()
        result = await self._call(
            "generate", lambda: self.inner.generate_response(request), retry=self._config.retry_generation
        )
        return result.with_latency(int((time.perf_counter() - started) * 1000))

    async def stream_response(self, request: GenerationRequest) -> AsyncIterator[GenerationChunk]:
        # Sin reintentos: lo ya emitido no se puede "deshacer"
        self.breaker.before_call(self.provider_type)
        self._take_token()
        async with self._slot():
            try:
                async with asyncio.timeout(self._config.request_timeout):
                    async for chunk in self.inner.stream_response(request):
                        yield chunk
            except TimeoutError:
                error = ProviderTimeoutError("El proveedor no respondió a tiempo", provider_type=self.provider_type)
                self._record_failure("stream", error)
                raise error from None
            except ProviderError as error:
                self._record_failure("stream", error)
                raise
        self.breaker.on_success()
        metrics.PROVIDER_REQUESTS.labels(self.provider_type, "stream", "success").inc()

    async def health_check(self) -> HealthStatus:
        """Sin breaker: la prueba de conexión debe poder confirmar que el proveedor volvió."""
        try:
            async with asyncio.timeout(self._config.request_timeout):
                status = await self.inner.health_check()
        except TimeoutError:
            return HealthStatus(False, None, ProviderTimeoutError.code, "El proveedor no respondió a tiempo")
        if status.healthy:
            self.breaker.on_success()
        return status

    # ── internos ───────────────────────────────────────────────────────────

    async def _call(self, operation: str, action: Callable[[], Awaitable[T]], *, retry: bool) -> T:
        attempts = self._retry.max_attempts if retry else 1
        attempt = 0
        while True:
            attempt += 1
            self.breaker.before_call(self.provider_type)
            self._take_token()
            started = time.perf_counter()
            try:
                async with self._slot():
                    async with asyncio.timeout(self._config.request_timeout):
                        result = await action()
            except TimeoutError:
                error: ProviderError = ProviderTimeoutError(
                    "El proveedor no respondió a tiempo", provider_type=self.provider_type
                )
            except ProviderError as e:
                error = e
            else:
                self.breaker.on_success()
                metrics.PROVIDER_REQUESTS.labels(self.provider_type, operation, "success").inc()
                metrics.PROVIDER_LATENCY.labels(self.provider_type, operation).observe(time.perf_counter() - started)
                return result
            self._record_failure(operation, error)
            if attempt >= attempts or not error.retryable or isinstance(error, (CircuitOpenError, ConcurrencyLimitError)):
                raise error
            await self._sleep(self._retry.delay(attempt, error))

    def _record_failure(self, operation: str, error: ProviderError) -> None:
        metrics.PROVIDER_REQUESTS.labels(self.provider_type, operation, error.code).inc()
        if self.breaker.on_failure(error):
            metrics.CIRCUIT_OPEN.labels(self.provider_type).inc()

    def _take_token(self) -> None:
        wait = self._bucket.try_acquire()
        if wait is not None:
            raise RateLimitError(
                "Se alcanzó el límite local de solicitudes por minuto de esta configuración",
                retry_after=wait,
                provider_type=self.provider_type,
            )

    def _slot(self):
        return _Slot(self._semaphore, self._config.request_timeout, self.provider_type)

    # ``AIProvider`` exige estos ganchos. Las operaciones públicas ya se delegan arriba con su protección; si alguien
    # llamara a un gancho directamente, también va al adaptador envuelto (nunca queda un método sin implementar).
    async def _open(self, settings: ProviderSettings) -> None:
        await self.inner.initialize(settings)

    async def _close(self) -> None:
        await self.inner.close()

    async def _list_models(self) -> list[ModelInfo]:
        return await self.inner.list_models()

    async def _generate(self, request: GenerationRequest) -> GenerationResult:
        return await self.inner.generate_response(request)

    def __repr__(self) -> str:
        return f"ResilientProvider({self.inner!r}, breaker={self.breaker.state})"


class _Slot:
    """Semáforo con espera acotada: si no hay lugar a tiempo, ``ConcurrencyLimitError``."""

    def __init__(self, semaphore: asyncio.Semaphore, timeout: float, provider_type: str) -> None:
        self._semaphore = semaphore
        self._timeout = timeout
        self._provider_type = provider_type

    async def __aenter__(self) -> None:
        try:
            async with asyncio.timeout(self._timeout):
                await self._semaphore.acquire()
        except TimeoutError:
            raise ConcurrencyLimitError(
                "Demasiadas solicitudes simultáneas para esta configuración", provider_type=self._provider_type
            ) from None

    async def __aexit__(self, *exc: object) -> None:
        self._semaphore.release()
