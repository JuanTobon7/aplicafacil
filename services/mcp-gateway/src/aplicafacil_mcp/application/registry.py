"""Registro de adaptadores vivos por configuración.

- Versionado e idempotente: un cambio con versión igual o menor a la ya vista se ignora.
- Reemplazo atómico: el adaptador nuevo se inicializa y verifica antes de cerrar el anterior. Si la versión nueva
  falla, el anterior también se retira (puede tener una key revocada), pero ninguna otra configuración se toca.
- Credenciales solo en memoria, con vencimiento (``credential_ttl``): al vencer, el adaptador se cierra y la
  credencial se vuelve a pedir al backend la próxima vez que se necesite.
- Un lock por configuración serializa eventos, reconciliación y cargas bajo demanda.
"""

from __future__ import annotations

import asyncio
import logging
import time
from collections.abc import Callable
from dataclasses import dataclass, field
from typing import Literal

from aplicafacil_mcp import metrics
from aplicafacil_mcp.application.ports import ConfigurationSource
from aplicafacil_mcp.config import ResilienceSettings
from aplicafacil_mcp.domain.errors import (
    AppError,
    ConfigDisabledError,
    ConfigNotFoundError,
    ProviderError,
)
from aplicafacil_mcp.domain.models import ProviderSettings
from aplicafacil_mcp.domain.sanitize import sanitize
from aplicafacil_mcp.providers.base import AIProvider
from aplicafacil_mcp.providers.factory import ProviderFactory
from aplicafacil_mcp.providers.resilience import ResilientProvider

log = logging.getLogger(__name__)

Clock = Callable[[], float]
MAX_REPORTED_MODELS = 500


@dataclass(frozen=True)
class InitOutcome:
    config_id: str
    user_id: str
    version: int
    status: Literal["ACTIVE", "FAILED", "REMOVED"]
    error_code: str | None = None
    error_message: str | None = None
    models: tuple[str, ...] = ()
    latency_ms: int | None = None


@dataclass
class _Entry:
    config_id: str
    user_id: str
    provider_type: str
    version: int
    provider: AIProvider | None
    outcome: InitOutcome
    expires_at: float = field(default=0.0)


class ProviderRegistry:
    def __init__(
        self,
        factory: ProviderFactory,
        source: ConfigurationSource,
        resilience: ResilienceSettings,
        *,
        credential_ttl: float = 3600.0,
        clock: Clock = time.monotonic,
    ) -> None:
        self._factory = factory
        self._source = source
        self._resilience = resilience
        self._ttl = credential_ttl
        self._clock = clock
        self._entries: dict[str, _Entry] = {}
        self._known_versions: dict[str, int] = {}
        self._locks: dict[str, asyncio.Lock] = {}

    # ── cambios de configuración (eventos y reconciliación) ───────────────

    def is_newer(self, config_id: str, version: int) -> bool:
        return version > self._known_versions.get(config_id, 0)

    def known_version(self, config_id: str) -> int:
        return self._known_versions.get(config_id, 0)

    async def apply(self, config_id: str, user_id: str, version: int, *, enabled: bool) -> InitOutcome | None:
        """Aplica un cambio. @return ``None`` si es repetido o atrasado (idempotencia)."""
        async with self._lock(config_id):
            if not self.is_newer(config_id, version):
                return None
            self._known_versions[config_id] = version
            if not enabled:
                await self._discard(config_id)
                return InitOutcome(config_id, user_id, version, "REMOVED")
            return await self._load(config_id, user_id, version)

    async def remove(self, config_id: str, version: int | None = None) -> None:
        async with self._lock(config_id):
            if version is not None:
                self._known_versions[config_id] = max(version, self._known_versions.get(config_id, 0))
            await self._discard(config_id)

    def outcome(self, config_id: str) -> InitOutcome | None:
        entry = self._entries.get(config_id)
        return entry.outcome if entry else None

    def loaded_config_ids(self) -> set[str]:
        return set(self._entries)

    # ── uso ────────────────────────────────────────────────────────────────

    async def get(self, config_id: str, user_id: str) -> AIProvider:
        """Adaptador listo para ``user_id``. Si no está cargado o su credencial venció, lo carga del backend
        (que verifica la propiedad). Nunca devuelve el adaptador de otra persona."""
        entry = self._entries.get(config_id)
        if entry is not None and entry.user_id != user_id:
            raise ConfigNotFoundError("La configuración no existe")
        if self._usable(entry):
            return entry.provider
        async with self._lock(config_id):
            entry = self._entries.get(config_id)
            if entry is not None and entry.user_id != user_id:
                raise ConfigNotFoundError("La configuración no existe")
            if not self._usable(entry):
                outcome = await self._load(config_id, user_id, None)
                if outcome.error_code == ConfigNotFoundError.code:
                    raise ConfigNotFoundError("La configuración no existe")
                if outcome.status == "REMOVED":
                    raise ConfigDisabledError("La configuración está desactivada")
                if outcome.status == "FAILED":
                    raise _failed(outcome)
                entry = self._entries[config_id]
            return entry.provider

    def runtime_state(self, config_id: str) -> dict[str, object] | None:
        entry = self._entries.get(config_id)
        if entry is None:
            return None
        breaker = entry.provider.breaker.state if isinstance(entry.provider, ResilientProvider) else None
        return {
            "loaded": entry.provider is not None,
            "version": entry.version,
            "status": entry.outcome.status,
            "circuit": breaker,
            "credential_expires_in_s": max(0, int(entry.expires_at - self._clock())) if entry.provider else None,
        }

    async def evict_expired(self) -> int:
        """Cierra los adaptadores cuya credencial venció (la saca de memoria aunque no se use)."""
        expired = [cid for cid, e in self._entries.items() if e.provider is not None and e.expires_at <= self._clock()]
        for config_id in expired:
            async with self._lock(config_id):
                entry = self._entries.get(config_id)
                if entry and entry.provider is not None and entry.expires_at <= self._clock():
                    await _close_quietly(entry.provider)
                    entry.provider = None
        self._update_gauge()
        return len(expired)

    async def close(self) -> None:
        for config_id in list(self._entries):
            await self._discard(config_id)

    # ── internos ───────────────────────────────────────────────────────────

    def _usable(self, entry: _Entry | None) -> bool:
        return (
            entry is not None
            and entry.provider is not None
            and entry.outcome.status == "ACTIVE"
            and entry.expires_at > self._clock()
        )

    async def _load(self, config_id: str, user_id: str, expected_version: int | None) -> InitOutcome:
        try:
            settings = await self._source.resolve(config_id, user_id)
        except ConfigNotFoundError:
            await self._discard(config_id)
            return InitOutcome(
                config_id,
                user_id,
                expected_version or self.known_version(config_id),
                "REMOVED",
                error_code=ConfigNotFoundError.code,
            )
        version = max(settings.version, expected_version or 0)
        self._known_versions[config_id] = max(version, self._known_versions.get(config_id, 0))
        if not settings.enabled:
            await self._discard(config_id)
            return InitOutcome(config_id, user_id, version, "REMOVED")

        started = time.perf_counter()
        provider: AIProvider | None = None
        try:
            provider = ResilientProvider(self._factory.create(settings.provider_type), self._resilience)
            await provider.initialize(settings)
            provider.validate_configuration()
            health = await provider.health_check()
            if not health.healthy:
                raise _HealthFailure(health.error_code, health.message)
            models = await self._models(provider)
        except (ProviderError, AppError, _HealthFailure) as e:
            if provider is not None:
                await _close_quietly(provider)
            outcome = InitOutcome(
                config_id,
                user_id,
                settings.version,
                "FAILED",
                error_code=getattr(e, "code", "INITIALIZATION_FAILED"),
                error_message=sanitize(getattr(e, "message", str(e)), settings.api_key.reveal()),
                latency_ms=_ms(started),
            )
            await self._replace(settings, None, outcome)
            metrics.PROVIDER_INITIALIZATIONS.labels(settings.provider_type, "failed").inc()
            log.warning(
                "Inicialización fallida",
                extra={"config_id": config_id, "version": settings.version, "error_code": outcome.error_code},
            )
            return outcome

        outcome = InitOutcome(config_id, user_id, settings.version, "ACTIVE", models=models, latency_ms=_ms(started))
        await self._replace(settings, provider, outcome)
        metrics.PROVIDER_INITIALIZATIONS.labels(settings.provider_type, "active").inc()
        log.info(
            "Adaptador listo",
            extra={"config_id": config_id, "version": settings.version, "provider_type": settings.provider_type},
        )
        return outcome

    async def _models(self, provider: AIProvider) -> tuple[str, ...]:
        if not provider.capabilities.model_listing:
            return ()
        try:
            return tuple(m.id for m in (await provider.list_models())[:MAX_REPORTED_MODELS])
        except ProviderError:
            return ()  # la key ya se validó; sin catálogo no es motivo para fallar

    async def _replace(self, settings: ProviderSettings, provider: AIProvider | None, outcome: InitOutcome) -> None:
        previous = self._entries.get(settings.config_id)
        self._entries[settings.config_id] = _Entry(
            config_id=settings.config_id,
            user_id=settings.user_id,
            provider_type=settings.provider_type,
            version=settings.version,
            provider=provider,
            outcome=outcome,
            expires_at=self._clock() + self._ttl,
        )
        if previous is not None and previous.provider is not None and previous.provider is not provider:
            await _close_quietly(previous.provider)
        self._update_gauge()

    async def _discard(self, config_id: str) -> None:
        entry = self._entries.pop(config_id, None)
        if entry is not None and entry.provider is not None:
            await _close_quietly(entry.provider)
        self._update_gauge()

    def _lock(self, config_id: str) -> asyncio.Lock:
        return self._locks.setdefault(config_id, asyncio.Lock())

    def _update_gauge(self) -> None:
        metrics.ACTIVE_PROVIDERS.set(sum(1 for e in self._entries.values() if e.provider is not None))


class _HealthFailure(Exception):
    def __init__(self, code: str | None, message: str | None) -> None:
        super().__init__(message)
        self.code = code or "HEALTH_CHECK_FAILED"
        self.message = message or "La verificación de la credencial falló"


def _failed(outcome: InitOutcome) -> ProviderError:
    error = ProviderError(outcome.error_message or "La configuración no se pudo inicializar")
    error.code = outcome.error_code or "INITIALIZATION_FAILED"
    return error


async def _close_quietly(provider: AIProvider) -> None:
    try:
        await provider.close()
    except Exception:  # cerrar nunca debe romper el reemplazo
        log.exception("Error al cerrar un adaptador")


def _ms(started: float) -> int:
    return int((time.perf_counter() - started) * 1000)
