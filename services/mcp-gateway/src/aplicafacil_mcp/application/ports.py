"""Puertos de salida de la aplicación. La infraestructura los implementa; los tests usan dobles."""

from __future__ import annotations

from typing import Protocol

from aplicafacil_mcp.contracts.messages import AiRequestResultEvent, ProviderStatusEvent
from aplicafacil_mcp.domain.models import ConfigSummary, ProviderSettings


class ConfigurationSource(Protocol):
    """Fuente de verdad de las configuraciones (ai-config-service)."""

    async def list_configs(self, user_id: str | None = None) -> list[ConfigSummary]:
        """Sin credenciales. ``user_id=None`` → todas (reconciliación)."""
        ...

    async def resolve(self, config_id: str, user_id: str) -> ProviderSettings:
        """Con la credencial. Lanza ``ConfigNotFoundError`` si no existe o no es de ``user_id``,
        ``BackendUnavailableError`` si el backend no responde."""
        ...


class EventPublisher(Protocol):
    async def publish_status(self, event: ProviderStatusEvent) -> None: ...

    async def publish_result(self, event: AiRequestResultEvent) -> None: ...


class NullPublisher:
    """Para correr sin RabbitMQ (``MCP_MESSAGING_ENABLED=false``) o en tests."""

    async def publish_status(self, event: ProviderStatusEvent) -> None:
        return None

    async def publish_result(self, event: AiRequestResultEvent) -> None:
        return None
