"""Representación interna común a todos los proveedores.

Ningún objeto de un SDK (openai, httpx…) cruza esta frontera: los adaptadores traducen hacia y desde estos tipos.
"""

from __future__ import annotations

from dataclasses import dataclass, field, replace
from typing import Literal

Role = Literal["system", "user", "assistant"]


class Secret:
    """Envuelve una API key: ``repr``/``str`` nunca la muestran. Solo ``reveal()`` la entrega al adaptador."""

    __slots__ = ("_value",)

    def __init__(self, value: str) -> None:
        if not value:
            raise ValueError("secreto vacío")
        self._value = value

    def reveal(self) -> str:
        return self._value

    def __repr__(self) -> str:
        return "Secret('****')"

    __str__ = __repr__

    def __eq__(self, other: object) -> bool:
        return isinstance(other, Secret) and other._value == self._value

    def __hash__(self) -> int:
        return hash(self._value)

    def __reduce__(self):  # no se serializa con pickle
        raise TypeError("Secret no se puede serializar")


@dataclass(frozen=True)
class ModelParameters:
    temperature: float | None = None
    max_tokens: int | None = None
    top_p: float | None = None

    def merged_with(self, override: ModelParameters | None) -> ModelParameters:
        """Los valores de ``override`` que no sean ``None`` ganan."""
        if override is None:
            return self
        return ModelParameters(
            temperature=override.temperature if override.temperature is not None else self.temperature,
            max_tokens=override.max_tokens if override.max_tokens is not None else self.max_tokens,
            top_p=override.top_p if override.top_p is not None else self.top_p,
        )


@dataclass(frozen=True)
class ProviderCapabilities:
    """Lo que un adaptador sabe hacer. Lo que no declara, lo rechaza con ``UnsupportedCapabilityError``."""

    streaming: bool
    model_listing: bool
    system_messages: bool = True
    supported_parameters: frozenset[str] = frozenset({"temperature", "max_tokens", "top_p"})
    custom_base_url: bool = False


@dataclass(frozen=True)
class ProviderSettings:
    """Configuración resuelta de una configuración de proveedor (incluye la credencial, solo en memoria)."""

    config_id: str
    user_id: str
    provider_type: str
    display_name: str
    model: str
    version: int
    api_key: Secret = field(repr=False)
    base_url: str | None = None
    parameters: ModelParameters = field(default_factory=ModelParameters)
    enabled: bool = True


@dataclass(frozen=True)
class ChatMessage:
    role: Role
    content: str


@dataclass(frozen=True)
class GenerationRequest:
    messages: tuple[ChatMessage, ...]
    model: str | None = None
    parameters: ModelParameters | None = None

    def __post_init__(self) -> None:
        if not self.messages:
            raise ValueError("Se necesita al menos un mensaje")


@dataclass(frozen=True)
class Usage:
    input_tokens: int | None = None
    output_tokens: int | None = None
    total_tokens: int | None = None


@dataclass(frozen=True)
class GenerationResult:
    text: str
    model: str
    provider_type: str
    finish_reason: str | None = None
    usage: Usage | None = None
    latency_ms: int | None = None

    def with_latency(self, latency_ms: int) -> GenerationResult:
        return replace(self, latency_ms=latency_ms)


@dataclass(frozen=True)
class GenerationChunk:
    delta: str
    finish_reason: str | None = None


@dataclass(frozen=True)
class ModelInfo:
    id: str
    owned_by: str | None = None
    context_length: int | None = None


@dataclass(frozen=True)
class HealthStatus:
    healthy: bool
    latency_ms: int | None = None
    error_code: str | None = None
    message: str | None = None


@dataclass(frozen=True)
class ConfigSummary:
    """Lo que ai-config-service informa de una configuración, sin credencial."""

    id: str
    user_id: str
    provider_type: str
    display_name: str
    enabled: bool
    model: str
    version: int
    status: str
    available_models: tuple[str, ...] = ()
