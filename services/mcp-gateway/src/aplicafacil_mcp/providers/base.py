"""Contrato de un proveedor de IA (``AIProvider``).

Toda implementación debe poder reemplazar a otra sin que la orquestación ni las tools MCP cambien (LSP). El
contrato de comportamiento, verificado para cada adaptador por ``tests/test_provider_contract.py``:

1. Ciclo de vida: ``initialize(settings)`` una sola vez → ``validate_configuration()`` → operaciones → ``close()``.
   Cualquier operación antes de ``initialize`` o después de ``close`` lanza ``ProviderNotInitializedError``.
   ``close()`` es idempotente.
2. ``initialize`` y ``validate_configuration`` no llaman a la red. Una configuración inválida lanza
   ``ConfigurationError``.
3. ``list_models``, ``generate_response`` y ``stream_response`` solo lanzan subclases de ``ProviderError``:
   401/403 → ``AuthenticationError``; 429 → ``RateLimitError``; 404 de modelo → ``ModelNotFoundError``;
   400/422 → ``InvalidRequestError``; 5xx o conexión → ``ProviderUnavailableError``; tiempo agotado →
   ``ProviderTimeoutError``.
4. Lo que el adaptador no declara en ``capabilities`` lanza ``UnsupportedCapabilityError`` (no se simula).
5. ``generate_response`` devuelve un ``GenerationResult`` normalizado: ``text`` nunca es ``None`` y ``model`` es el
   modelo que respondió (o el pedido si el proveedor no lo informa).
6. ``health_check`` **no lanza** errores de proveedor: devuelve ``HealthStatus(healthy=False, error_code=...)``.
7. Ningún mensaje de error ni ``repr`` incluye la API key.
"""

from __future__ import annotations

import abc
from collections.abc import AsyncIterator
from dataclasses import dataclass
from typing import ClassVar

from aplicafacil_mcp.domain.errors import (
    ConfigurationError,
    ProviderNotInitializedError,
    UnsupportedCapabilityError,
)
from aplicafacil_mcp.domain.models import (
    GenerationChunk,
    GenerationRequest,
    GenerationResult,
    HealthStatus,
    ModelInfo,
    ModelParameters,
    ProviderCapabilities,
    ProviderSettings,
)


@dataclass(frozen=True)
class AdapterOptions:
    """Lo que la fábrica entrega a cada adaptador, igual para todos."""

    connect_timeout: float = 5.0
    request_timeout: float = 60.0


class AIProvider(abc.ABC):
    provider_type: ClassVar[str]
    capabilities: ClassVar[ProviderCapabilities]

    def __init__(self, options: AdapterOptions | None = None) -> None:
        self.options = options or AdapterOptions()
        self._settings: ProviderSettings | None = None
        self._closed = False

    # ── ciclo de vida ──────────────────────────────────────────────────────

    async def initialize(self, settings: ProviderSettings) -> None:
        if self._settings is not None:
            raise ConfigurationError("El adaptador ya fue inicializado", provider_type=self.provider_type)
        if settings.provider_type != self.provider_type:
            raise ConfigurationError(
                f"Configuración de {settings.provider_type} entregada al adaptador {self.provider_type}",
                provider_type=self.provider_type,
            )
        self._settings = settings
        await self._open(settings)

    def validate_configuration(self) -> None:
        """Verificaciones locales comunes; los adaptadores agregan las suyas en ``_validate``."""
        settings = self.settings
        if not settings.model or not settings.model.strip():
            raise ConfigurationError("Falta el modelo", provider_type=self.provider_type)
        if settings.base_url and not self.capabilities.custom_base_url:
            raise ConfigurationError("Este proveedor no admite una URL personalizada", provider_type=self.provider_type)
        if settings.base_url and not _allowed_base_url(settings.base_url):
            raise ConfigurationError(
                "La URL debe usar https (o http solo hacia 127.0.0.1/localhost)", provider_type=self.provider_type
            )
        self._validate(settings)

    async def close(self) -> None:
        if self._closed:
            return
        self._closed = True
        if self._settings is not None:
            await self._close()

    # ── operaciones ────────────────────────────────────────────────────────

    async def list_models(self) -> list[ModelInfo]:
        self._ensure_ready()
        if not self.capabilities.model_listing:
            raise UnsupportedCapabilityError("Este proveedor no permite listar modelos", provider_type=self.provider_type)
        return await self._list_models()

    async def generate_response(self, request: GenerationRequest) -> GenerationResult:
        self._ensure_ready()
        self._check_request(request)
        return await self._generate(request)

    async def stream_response(self, request: GenerationRequest) -> AsyncIterator[GenerationChunk]:
        self._ensure_ready()
        if not self.capabilities.streaming:
            raise UnsupportedCapabilityError("Este proveedor no soporta streaming", provider_type=self.provider_type)
        self._check_request(request)
        async for chunk in self._stream(request):
            yield chunk

    @abc.abstractmethod
    async def health_check(self) -> HealthStatus:
        """No lanza errores de proveedor: los informa en el ``HealthStatus``."""

    # ── para los adaptadores ───────────────────────────────────────────────

    @property
    def settings(self) -> ProviderSettings:
        if self._settings is None:
            raise ProviderNotInitializedError("El adaptador no fue inicializado", provider_type=self.provider_type)
        return self._settings

    @property
    def is_ready(self) -> bool:
        return self._settings is not None and not self._closed

    def effective_model(self, request: GenerationRequest) -> str:
        return request.model or self.settings.model

    def effective_parameters(self, request: GenerationRequest) -> ModelParameters:
        return self.settings.parameters.merged_with(request.parameters)

    def _ensure_ready(self) -> None:
        if self._closed:
            raise ProviderNotInitializedError("El adaptador está cerrado", provider_type=self.provider_type)
        _ = self.settings

    def _check_request(self, request: GenerationRequest) -> None:
        if not self.capabilities.system_messages and any(m.role == "system" for m in request.messages):
            raise UnsupportedCapabilityError(
                "Este proveedor no admite mensajes de sistema", provider_type=self.provider_type
            )

    @abc.abstractmethod
    async def _open(self, settings: ProviderSettings) -> None:
        """Crea el cliente del SDK. No llama a la red."""

    def _validate(self, settings: ProviderSettings) -> None:  # opcional
        """Validaciones propias del adaptador (sin red)."""

    @abc.abstractmethod
    async def _close(self) -> None: ...

    @abc.abstractmethod
    async def _list_models(self) -> list[ModelInfo]: ...

    @abc.abstractmethod
    async def _generate(self, request: GenerationRequest) -> GenerationResult: ...

    async def _stream(self, request: GenerationRequest) -> AsyncIterator[GenerationChunk]:
        raise UnsupportedCapabilityError("Este proveedor no soporta streaming", provider_type=self.provider_type)
        yield  # convierte el método en generador

    def __repr__(self) -> str:
        config = self._settings.config_id if self._settings else None
        return f"{type(self).__name__}(config_id={config!r}, ready={self.is_ready})"


def _allowed_base_url(url: str) -> bool:
    from urllib.parse import urlsplit

    parts = urlsplit(url)
    if parts.username or parts.password or not parts.hostname:
        return False
    if parts.scheme == "https":
        return True
    return parts.scheme == "http" and parts.hostname in {"127.0.0.1", "localhost"}
