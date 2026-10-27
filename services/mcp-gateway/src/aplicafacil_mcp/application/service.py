"""``AIService``: casos de uso que comparten las tools MCP y los comandos de RabbitMQ.

Depende solo de ``ProviderRegistry`` (que entrega ``AIProvider``) y de puertos. No conoce ningún proveedor:
agregar uno no cambia este módulo.
"""

from __future__ import annotations

import logging
import time
from collections import OrderedDict
from collections.abc import AsyncIterator
from dataclasses import dataclass
from datetime import UTC, datetime

from aplicafacil_mcp.application.ports import ConfigurationSource, EventPublisher
from aplicafacil_mcp.application.registry import ProviderRegistry
from aplicafacil_mcp.contracts.messages import (
    AiRequestCommand,
    AiRequestResultEvent,
    ErrorDto,
    OutputDto,
    ProviderStatusEvent,
    ProviderTestCommand,
    UsageDto,
)
from aplicafacil_mcp.domain.errors import AppError, ConfigNotFoundError
from aplicafacil_mcp.domain.models import (
    ChatMessage,
    ConfigSummary,
    GenerationChunk,
    GenerationRequest,
    GenerationResult,
    HealthStatus,
    ModelInfo,
    ModelParameters,
    ProviderCapabilities,
)
from aplicafacil_mcp.domain.sanitize import sanitize
from aplicafacil_mcp.providers.factory import ProviderFactory

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class ProviderView:
    config: ConfigSummary
    capabilities: ProviderCapabilities | None
    runtime: dict[str, object] | None


class ProcessedIds:
    """Memoria acotada de comandos ya procesados en esta instancia (idempotencia ante reentregas).

    Las copias de un mismo comando pueden llegar a la vez (prefetch > 1): ``claim`` lo marca como "en curso" ANTES
    de procesarlo, así la segunda copia se descarta aunque la primera no haya terminado.
    """

    def __init__(self, capacity: int = 10_000) -> None:
        self._capacity = capacity
        self._ids: OrderedDict[str, None] = OrderedDict()
        self._in_progress: set[str] = set()

    def seen(self, key: str) -> bool:
        return key in self._ids

    def claim(self, key: str) -> bool:
        """@return False si ya se procesó o lo está procesando otra copia."""
        if key in self._ids or key in self._in_progress:
            return False
        self._in_progress.add(key)
        return True

    def release(self, key: str) -> None:
        """Libera el reclamo sin marcarlo procesado (falló: un reintento podrá tomarlo)."""
        self._in_progress.discard(key)

    def add(self, key: str) -> None:
        self._in_progress.discard(key)
        self._ids[key] = None
        self._ids.move_to_end(key)
        while len(self._ids) > self._capacity:
            self._ids.popitem(last=False)


class AIService:
    def __init__(
        self,
        registry: ProviderRegistry,
        source: ConfigurationSource,
        factory: ProviderFactory,
        publisher: EventPublisher,
        *,
        instance_id: str,
    ) -> None:
        self._registry = registry
        self._source = source
        self._factory = factory
        self._publisher = publisher
        self._instance_id = instance_id
        self._processed = ProcessedIds()

    # ── consultas (tools) ──────────────────────────────────────────────────

    async def list_providers(self, user_id: str) -> list[ProviderView]:
        configs = await self._source.list_configs(user_id)
        return [
            ProviderView(
                config=c,
                capabilities=self._factory.capabilities(c.provider_type) if self._factory.supports(c.provider_type) else None,
                runtime=self._registry.runtime_state(c.id),
            )
            for c in configs
            if c.user_id == user_id
        ]

    async def get_status(self, user_id: str, config_id: str) -> ProviderView:
        for view in await self.list_providers(user_id):
            if view.config.id == config_id:
                return view
        raise ConfigNotFoundError("La configuración no existe")

    async def list_models(self, user_id: str, config_id: str) -> list[ModelInfo]:
        provider = await self._registry.get(config_id, user_id)
        return await provider.list_models()

    async def generate(
        self,
        user_id: str,
        config_id: str,
        messages: list[ChatMessage],
        *,
        model: str | None = None,
        parameters: ModelParameters | None = None,
    ) -> GenerationResult:
        provider = await self._registry.get(config_id, user_id)
        return await provider.generate_response(GenerationRequest(tuple(messages), model, parameters))

    async def stream(
        self,
        user_id: str,
        config_id: str,
        messages: list[ChatMessage],
        *,
        model: str | None = None,
        parameters: ModelParameters | None = None,
    ) -> AsyncIterator[GenerationChunk]:
        provider = await self._registry.get(config_id, user_id)
        async for chunk in provider.stream_response(GenerationRequest(tuple(messages), model, parameters)):
            yield chunk

    async def test_connection(self, user_id: str, config_id: str) -> HealthStatus:
        try:
            provider = await self._registry.get(config_id, user_id)
        except ConfigNotFoundError:
            raise
        except AppError as e:
            return HealthStatus(False, None, e.code, e.message)
        return await provider.health_check()

    # ── comandos de RabbitMQ ───────────────────────────────────────────────

    async def handle_test_command(self, command: ProviderTestCommand) -> ProviderStatusEvent | None:
        return await self._once(command.command_id, lambda: self._run_test_command(command))

    async def handle_request_command(self, command: AiRequestCommand) -> AiRequestResultEvent | None:
        """Ejecuta ``ai.request.submit`` y publica el resultado. Repetidos (mismo requestId) se ignoran."""
        return await self._once(command.request_id, lambda: self._run_request_command(command))

    async def _once(self, key: str, action):
        """Idempotencia: cada comando se ejecuta una vez por instancia, aunque lleguen copias a la vez."""
        if not self._processed.claim(key):
            log.info("Comando repetido ignorado", extra={"command_id": key})
            return None
        try:
            result = await action()
        except BaseException:
            self._processed.release(key)  # un reintento del consumidor podrá tomarlo
            raise
        self._processed.add(key)
        return result

    async def _run_test_command(self, command: ProviderTestCommand) -> ProviderStatusEvent:
        started = time.perf_counter()
        try:
            health = await self.test_connection(command.user_id, command.config_id)
        except ConfigNotFoundError as e:
            health = HealthStatus(False, None, e.code, e.message)
        event = ProviderStatusEvent(
            event_type="ai.provider.test-completed",
            config_id=command.config_id,
            user_id=command.user_id,
            config_version=command.config_version,
            status="ACTIVE" if health.healthy else "FAILED",
            error_code=health.error_code,
            error_message=sanitize(health.message),
            latency_ms=health.latency_ms if health.latency_ms is not None else int((time.perf_counter() - started) * 1000),
            correlation_id=command.correlation_id,
            instance_id=self._instance_id,
        )
        await self._publisher.publish_status(event)
        return event

    async def _run_request_command(self, command: AiRequestCommand) -> AiRequestResultEvent | None:
        if command.deadline <= datetime.now(UTC):
            # El backend ya la cerró como TIMED_OUT: no se gasta una llamada al proveedor
            log.info("Solicitud vencida descartada", extra={"request_id": command.request_id})
            return None

        params = command.parameters
        started = time.perf_counter()
        try:
            result = await self.generate(
                command.user_id,
                command.config_id,
                [ChatMessage(m.role, m.content) for m in command.messages],
                model=params.model if params else None,
                parameters=ModelParameters(params.temperature, params.max_tokens, params.top_p) if params else None,
            )
            event = AiRequestResultEvent(
                event_type="ai.request.completed",
                request_id=command.request_id,
                config_id=command.config_id,
                user_id=command.user_id,
                correlation_id=command.correlation_id,
                output=OutputDto(
                    text=result.text,
                    model=result.model,
                    finish_reason=result.finish_reason,
                    usage=UsageDto(
                        input_tokens=result.usage.input_tokens,
                        output_tokens=result.usage.output_tokens,
                        total_tokens=result.usage.total_tokens,
                    )
                    if result.usage
                    else None,
                ),
                latency_ms=result.latency_ms,
            )
        except AppError as e:
            event = AiRequestResultEvent(
                event_type="ai.request.failed",
                request_id=command.request_id,
                config_id=command.config_id,
                user_id=command.user_id,
                correlation_id=command.correlation_id,
                error=ErrorDto(code=e.code[:64], message=sanitize(e.message) or e.code, retryable=e.retryable),
                latency_ms=int((time.perf_counter() - started) * 1000),
            )
        await self._publisher.publish_result(event)
        return event
