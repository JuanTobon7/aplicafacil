"""Integración con un RabbitMQ real (Testcontainers, misma imagen que infra/docker-compose.yml).

Flujo: evento de configuración → INITIALIZING/ACTIVE; comando ai.request.submit → ai.request.completed con el
mismo requestId y correlationId; reentrega ignorada; mensaje inválido → DLQ. Se omite si Docker no está.
"""

from __future__ import annotations

import asyncio
import json
import os
import subprocess
import uuid
from datetime import UTC, datetime, timedelta

import aio_pika
import pytest
from fakes import FAST, echo_factory

from aplicafacil_mcp.config import Settings
from aplicafacil_mcp.container import Container
from aplicafacil_mcp.infrastructure.messaging import COMMANDS_EXCHANGE, COMMANDS_QUEUE, EVENTS_EXCHANGE

pytestmark = pytest.mark.integration


def _use_active_docker_context() -> None:
    """docker-py usa el endpoint por defecto; Docker Desktop puede exponer otro (contexto ``desktop-linux``)."""
    if os.environ.get("DOCKER_HOST"):
        return
    try:
        host = subprocess.run(
            ["docker", "context", "inspect", "--format", "{{.Endpoints.docker.Host}}"],
            capture_output=True, text=True, timeout=10, check=True,
        ).stdout.strip()
    except (OSError, subprocess.SubprocessError):
        return
    if host:
        os.environ["DOCKER_HOST"] = host


@pytest.fixture(scope="module")
def rabbit_url():
    # Un broker ya levantado (p. ej. el de infra/ en un vhost de pruebas) en lugar de Testcontainers
    if os.environ.get("MCP_TEST_RABBITMQ_URL"):
        yield os.environ["MCP_TEST_RABBITMQ_URL"]
        return
    try:
        from testcontainers.community.rabbitmq import RabbitMqContainer
    except ImportError:
        try:
            from testcontainers.rabbitmq import RabbitMqContainer
        except ImportError:
            pytest.skip("testcontainers no instalado")
    _use_active_docker_context()
    try:
        container = RabbitMqContainer("rabbitmq:4.3-management")
        container.start()
    except Exception as e:  # sin Docker
        pytest.skip(f"Docker no disponible: {type(e).__name__}")
    params = container.get_connection_params()
    yield f"amqp://guest:guest@{params.host}:{params.port}/"
    container.stop()


@pytest.fixture
async def gateway(rabbit_url, source):
    settings = Settings(rabbitmq_url=rabbit_url, resilience=FAST, reconcile_interval_seconds=3600, log_format="text")
    container = Container(settings, source=source, factory=echo_factory(), token_verifier=object())
    await container.start()
    for _ in range(100):
        if container.broker.is_connected and container.broker._consumers:
            break
        await asyncio.sleep(0.05)
    yield container
    await container.stop()


@pytest.fixture
async def probe(rabbit_url):
    """Cola exclusiva que escucha lo que publica el mcp-gateway en aplicafacil.events."""
    connection = await aio_pika.connect_robust(rabbit_url)
    channel = await connection.channel()
    events = await channel.declare_exchange(EVENTS_EXCHANGE, aio_pika.ExchangeType.TOPIC, durable=True)
    commands = await channel.declare_exchange(COMMANDS_EXCHANGE, aio_pika.ExchangeType.TOPIC, durable=True)
    queue = await channel.declare_queue(exclusive=True)
    await queue.bind(events, "ai.provider.#")
    await queue.bind(events, "ai.request.*")

    async def receive(count: int, timeout: float = 10) -> list[tuple[str, dict, aio_pika.abc.AbstractIncomingMessage]]:
        received = []
        deadline = asyncio.get_running_loop().time() + timeout
        while len(received) < count and asyncio.get_running_loop().time() < deadline:
            message = await queue.get(fail=False)
            if message is None:
                await asyncio.sleep(0.05)
                continue
            await message.ack()
            received.append((message.routing_key, json.loads(message.body), message))
        return received

    yield events, commands, receive, channel
    await connection.close()


def publish(exchange, routing_key: str, payload: dict | bytes, correlation_id: str | None = None):
    body = payload if isinstance(payload, bytes) else json.dumps(payload).encode()
    return exchange.publish(
        aio_pika.Message(body, content_type="application/json", correlation_id=correlation_id), routing_key=routing_key
    )


async def test_end_to_end_configuration_and_request_correlation(gateway, probe, source):
    events, commands, receive, _ = probe
    s = source.add()
    correlation = str(uuid.uuid4())
    await publish(
        events,
        "ai.provider-config.created",
        {
            "schemaVersion": 1,
            "eventId": str(uuid.uuid4()),
            "eventType": "ai.provider-config.created",
            "configId": s.config_id,
            "userId": s.user_id,
            "version": 1,
            "providerType": "ECHO",
            "enabled": True,
            "correlationId": correlation,
            "occurredAt": datetime.now(UTC).isoformat(),
        },
        correlation,
    )
    statuses = await receive(2)
    assert [(rk, body["status"]) for rk, body, _ in statuses] == [
        ("ai.provider.initialization-started", "INITIALIZING"),
        ("ai.provider.initialization-succeeded", "ACTIVE"),
    ]
    assert statuses[1][2].correlation_id == correlation
    assert statuses[1][2].delivery_mode == aio_pika.DeliveryMode.PERSISTENT

    request_id, request_corr = str(uuid.uuid4()), str(uuid.uuid4())
    now = datetime.now(UTC)
    command = {
        "schemaVersion": 1,
        "requestId": request_id,
        "configId": s.config_id,
        "userId": s.user_id,
        "correlationId": request_corr,
        "messages": [{"role": "user", "content": "hola"}],
        "parameters": None,
        "submittedAt": now.isoformat(),
        "deadline": (now + timedelta(minutes=1)).isoformat(),
    }
    await publish(commands, "ai.request.submit", command, request_corr)
    await publish(commands, "ai.request.submit", command, request_corr)  # reentrega
    results = await receive(2, timeout=3)
    assert len(results) == 1
    routing_key, body, message = results[0]
    assert routing_key == "ai.request.completed"
    assert (body["requestId"], body["correlationId"]) == (request_id, request_corr)
    assert body["output"]["text"] == "echo: hola"
    assert message.message_id == body["eventId"]


async def test_invalid_command_goes_to_the_dead_letter_queue(gateway, probe):
    _, commands, _, channel = probe
    await publish(commands, "ai.request.submit", b'{"no": "cumple"}')
    dlq = await channel.declare_queue(COMMANDS_QUEUE + ".dlq", durable=True, passive=True)
    for _ in range(100):
        message = await dlq.get(fail=False)
        if message is not None:
            await message.ack()
            assert b"cumple" in message.body
            return
        await asyncio.sleep(0.05)
    pytest.fail("el mensaje inválido no llegó a la DLQ")


async def test_readiness_reflects_the_broker_connection(gateway):
    state = gateway.readiness()
    assert state["checks"]["rabbitmq"] is True
