"""RabbitMQ con aio-pika: topología, consumidores y publicador (contracts/events/asyncapi.yaml).

- ``mcp-gateway.provider-config.<instancia>``: exclusiva y temporal; cada instancia recibe todos los cambios de
  configuración (lo perdido mientras no existía lo recupera la reconciliación).
- ``mcp-gateway.commands``: durable y compartida (reparto de trabajo), con DLQ ``mcp-gateway.commands.dlq``.
- Mensajes que no cumplen el contrato → DLQ sin reintentos. Fallos transitorios → hasta ``max_attempts`` en
  proceso con backoff exponencial y jitter, luego DLQ. Todo se confirma (ack) solo después de procesarlo.
- Publicación persistente con confirmaciones del broker.
"""

from __future__ import annotations

import asyncio
import logging
import random
import re
import time
from collections.abc import Awaitable, Callable
from dataclasses import dataclass

import aio_pika
from aio_pika.abc import AbstractIncomingMessage, AbstractRobustConnection
from pydantic import BaseModel, ValidationError

from aplicafacil_mcp import metrics
from aplicafacil_mcp.contracts.messages import (
    AiRequestCommand,
    AiRequestResultEvent,
    ProviderConfigChanged,
    ProviderStatusEvent,
    ProviderTestCommand,
)
from aplicafacil_mcp.logging_setup import correlation_id_var

log = logging.getLogger(__name__)

EVENTS_EXCHANGE = "aplicafacil.events"
COMMANDS_EXCHANGE = "aplicafacil.commands"
DEAD_LETTER_EXCHANGE = "aplicafacil.dlx"
COMMANDS_QUEUE = "mcp-gateway.commands"
CONFIG_QUEUE_PREFIX = "mcp-gateway.provider-config."

Handler = Callable[[BaseModel], Awaitable[object]]


@dataclass(frozen=True)
class BrokerHandlers:
    """Qué caso de uso atiende cada tipo de mensaje."""

    on_config_changed: Handler
    on_test_command: Handler
    on_request_command: Handler


class RabbitBroker:
    def __init__(
        self,
        url: str,
        instance_id: str,
        handlers: BrokerHandlers,
        *,
        prefetch: int = 4,
        max_attempts: int = 3,
    ) -> None:
        self._url = url
        self.config_queue_name = CONFIG_QUEUE_PREFIX + re.sub(r"[^A-Za-z0-9_.-]", "-", instance_id)
        self._handlers: dict[str, tuple[type[BaseModel], Handler]] = {
            "ai.provider.test": (ProviderTestCommand, handlers.on_test_command),
            "ai.request.submit": (AiRequestCommand, handlers.on_request_command),
        }
        self._on_config_changed = handlers.on_config_changed
        self._prefetch = prefetch
        self._max_attempts = max_attempts
        self._connection: AbstractRobustConnection | None = None
        self._publish_channel: aio_pika.abc.AbstractChannel | None = None
        self._events: aio_pika.abc.AbstractExchange | None = None
        self._consumers: list[tuple[aio_pika.abc.AbstractQueue, str]] = []
        self._in_flight: set[asyncio.Task[None]] = set()
        self._commands_queue: aio_pika.abc.AbstractQueue | None = None

    @property
    def is_connected(self) -> bool:
        return self._connection is not None and not self._connection.is_closed

    async def start(self) -> None:
        self._connection = await aio_pika.connect_robust(self._url, client_properties={"connection_name": "mcp-gateway"})
        self._publish_channel = await self._connection.channel(publisher_confirms=True)
        self._events = await self._publish_channel.declare_exchange(EVENTS_EXCHANGE, aio_pika.ExchangeType.TOPIC, durable=True)

        consume = await self._connection.channel()
        await consume.set_qos(prefetch_count=self._prefetch)
        events = await consume.declare_exchange(EVENTS_EXCHANGE, aio_pika.ExchangeType.TOPIC, durable=True)
        commands = await consume.declare_exchange(COMMANDS_EXCHANGE, aio_pika.ExchangeType.TOPIC, durable=True)
        dlx = await consume.declare_exchange(DEAD_LETTER_EXCHANGE, aio_pika.ExchangeType.TOPIC, durable=True)

        # RabbitMQ 4 no admite colas transitorias no exclusivas: esta es exclusiva de la conexión
        config_queue = await consume.declare_queue(self.config_queue_name, exclusive=True, auto_delete=True)
        await config_queue.bind(events, "ai.provider-config.#")

        dlq = await consume.declare_queue(COMMANDS_QUEUE + ".dlq", durable=True)
        await dlq.bind(dlx, COMMANDS_QUEUE)
        self._commands_queue = await consume.declare_queue(
            COMMANDS_QUEUE,
            durable=True,
            arguments={"x-dead-letter-exchange": DEAD_LETTER_EXCHANGE, "x-dead-letter-routing-key": COMMANDS_QUEUE},
        )
        for routing_key in self._handlers:
            await self._commands_queue.bind(commands, routing_key)

        self._consumers = [
            (config_queue, await config_queue.consume(self._on_config_message)),
            (self._commands_queue, await self._commands_queue.consume(self._on_command_message)),
        ]
        log.info("Consumiendo de RabbitMQ", extra={"queues": [self.config_queue_name, COMMANDS_QUEUE]})

    async def stop(self, timeout: float = 30.0) -> None:
        """Apagado ordenado: deja de recibir, espera lo que está en curso y cierra la conexión."""
        for queue, tag in self._consumers:
            try:
                await queue.cancel(tag)
            except Exception:
                pass
        self._consumers = []
        if self._in_flight:
            await asyncio.wait(self._in_flight, timeout=timeout)
        if self._connection is not None:
            await self._connection.close()
            self._connection = None

    async def commands_queue_depth(self) -> int | None:
        if self._commands_queue is None or not self.is_connected:
            return None
        declared = await self._commands_queue.channel.declare_queue(COMMANDS_QUEUE, passive=True)
        depth = declared.declaration_result.message_count
        metrics.QUEUE_DEPTH.labels(COMMANDS_QUEUE).set(depth)
        return depth

    # ── publicador (EventPublisher) ────────────────────────────────────────

    async def publish_status(self, event: ProviderStatusEvent) -> None:
        await self._publish(event.event_type, event.event_id, event.correlation_id, event.to_json())

    async def publish_result(self, event: AiRequestResultEvent) -> None:
        await self._publish(event.event_type, event.event_id, event.correlation_id, event.to_json())

    async def _publish(self, routing_key: str, message_id: str, correlation_id: str, body: bytes) -> None:
        if self._events is None:
            raise RuntimeError("RabbitMQ no está conectado")
        await self._events.publish(
            aio_pika.Message(
                body,
                content_type="application/json",
                delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
                message_id=message_id,
                correlation_id=correlation_id,
                type=routing_key,
                app_id="mcp-gateway",
            ),
            routing_key=routing_key,
        )

    # ── consumidores ───────────────────────────────────────────────────────

    async def _on_config_message(self, message: AbstractIncomingMessage) -> None:
        await self._track(self._process(message, ProviderConfigChanged, self._on_config_changed, dead_letter=False))

    async def _on_command_message(self, message: AbstractIncomingMessage) -> None:
        entry = self._handlers.get(message.routing_key or "")
        if entry is None:
            await message.reject(requeue=False)
            return
        await self._track(self._process(message, entry[0], entry[1], dead_letter=True))

    async def _track(self, coro: Awaitable[None]) -> None:
        task = asyncio.ensure_future(coro)
        self._in_flight.add(task)
        try:
            await task
        finally:
            self._in_flight.discard(task)

    async def _process(
        self, message: AbstractIncomingMessage, model: type[BaseModel], handler: Handler, *, dead_letter: bool
    ) -> None:
        kind = message.routing_key or model.__name__
        token = correlation_id_var.set(message.correlation_id or "-")
        started = time.perf_counter()
        try:
            try:
                payload = model.model_validate_json(message.body)
            except ValidationError as e:
                log.warning("Mensaje que no cumple el contrato", extra={"type": kind, "errors": e.error_count()})
                metrics.MESSAGES_PROCESSED.labels(kind, "invalid").inc()
                await (message.reject(requeue=False) if dead_letter else message.ack())
                return
            for attempt in range(1, self._max_attempts + 1):
                try:
                    await handler(payload)
                except Exception:  # se reintenta y luego va a la DLQ
                    log.exception("Fallo procesando mensaje", extra={"type": kind, "attempt": attempt})
                    if attempt < self._max_attempts:
                        await asyncio.sleep(random.uniform(0, 0.5 * 2**attempt))
                        continue
                    metrics.MESSAGES_PROCESSED.labels(kind, "dead_lettered").inc()
                    await (message.reject(requeue=False) if dead_letter else message.ack())
                    return
                metrics.MESSAGES_PROCESSED.labels(kind, "ok").inc()
                await message.ack()
                return
        finally:
            metrics.MESSAGE_PROCESSING.labels(kind).observe(time.perf_counter() - started)
            correlation_id_var.reset(token)
