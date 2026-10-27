"""Mensajes de RabbitMQ (contracts/schemas/ai-*.schema.json). Ninguno tiene un campo para credenciales."""

from __future__ import annotations

import uuid
from datetime import UTC, datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

SCHEMA_VERSION = 1


def now() -> datetime:
    return datetime.now(UTC)


def new_id() -> str:
    return str(uuid.uuid4())


class _Message(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, extra="forbid", frozen=True)

    def to_json(self) -> bytes:
        return self.model_dump_json(by_alias=True, exclude_none=True).encode()


class _Incoming(_Message):
    # Tolerante a campos nuevos (compatibilidad hacia adelante del productor)
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, extra="ignore", frozen=True)


# ── Entrantes ────────────────────────────────────────────────────────────


class ProviderConfigChanged(_Incoming):
    """``ai.provider-config.*`` publicado por ai-config-service."""

    schema_version: int
    event_id: str
    event_type: Literal[
        "ai.provider-config.created",
        "ai.provider-config.updated",
        "ai.provider-config.disabled",
        "ai.provider-config.deleted",
        "ai.provider-config.credential-rotated",
    ]
    config_id: str
    user_id: str
    version: int = Field(ge=1)
    provider_type: str
    enabled: bool
    correlation_id: str
    occurred_at: datetime

    @property
    def removes(self) -> bool:
        return self.event_type in ("ai.provider-config.deleted", "ai.provider-config.disabled") or not self.enabled


class ProviderTestCommand(_Incoming):
    """``ai.provider.test``."""

    schema_version: int
    command_id: str
    config_id: str
    user_id: str
    config_version: int
    correlation_id: str
    requested_at: datetime


class ChatMessageDto(_Incoming):
    role: Literal["system", "user", "assistant"]
    content: str = Field(min_length=1, max_length=32000)


class RequestParametersDto(_Incoming):
    model: str | None = Field(default=None, max_length=200)
    temperature: float | None = Field(default=None, ge=0, le=2)
    max_tokens: int | None = Field(default=None, ge=1, le=200000)
    top_p: float | None = Field(default=None, ge=0, le=1)


class AiRequestCommand(_Incoming):
    """``ai.request.submit``."""

    schema_version: int
    request_id: str
    config_id: str
    user_id: str
    correlation_id: str
    messages: list[ChatMessageDto] = Field(min_length=1, max_length=50)
    parameters: RequestParametersDto | None = None
    submitted_at: datetime
    deadline: datetime


# ── Salientes ────────────────────────────────────────────────────────────

StatusEventType = Literal[
    "ai.provider.initialization-started",
    "ai.provider.initialization-succeeded",
    "ai.provider.initialization-failed",
    "ai.provider.test-completed",
]


class ProviderStatusEvent(_Message):
    """``ai.provider.*`` hacia ai-config-service. ``error_message`` ya saneado."""

    schema_version: int = SCHEMA_VERSION
    event_id: str = Field(default_factory=new_id)
    event_type: StatusEventType
    config_id: str
    user_id: str
    config_version: int
    status: Literal["INITIALIZING", "ACTIVE", "FAILED"]
    error_code: str | None = Field(default=None, max_length=64)
    error_message: str | None = Field(default=None, max_length=500)
    models: list[str] | None = Field(default=None, max_length=500)
    latency_ms: int | None = Field(default=None, ge=0)
    correlation_id: str
    occurred_at: datetime = Field(default_factory=now)
    instance_id: str


class UsageDto(_Message):
    input_tokens: int | None = None
    output_tokens: int | None = None
    total_tokens: int | None = None


class OutputDto(_Message):
    text: str
    model: str
    finish_reason: str | None = None
    usage: UsageDto | None = None


class ErrorDto(_Message):
    code: str = Field(max_length=64)
    message: str = Field(max_length=500)
    retryable: bool


class AiRequestResultEvent(_Message):
    """``ai.request.completed`` | ``ai.request.failed``."""

    schema_version: int = SCHEMA_VERSION
    event_id: str = Field(default_factory=new_id)
    event_type: Literal["ai.request.completed", "ai.request.failed"]
    request_id: str
    config_id: str
    user_id: str
    correlation_id: str
    output: OutputDto | None = None
    error: ErrorDto | None = None
    latency_ms: int | None = Field(default=None, ge=0)
    occurred_at: datetime = Field(default_factory=now)
