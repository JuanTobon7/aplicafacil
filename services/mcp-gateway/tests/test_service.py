"""AIService (comandos) y ConfigurationSync (eventos + reconciliación) con dobles."""

from __future__ import annotations

import uuid
from datetime import UTC, datetime, timedelta

import pytest
from fakes import API_KEY, FAST, EchoProviderAdapter, echo_factory

from aplicafacil_mcp.application.config_sync import ConfigurationSync
from aplicafacil_mcp.application.registry import ProviderRegistry
from aplicafacil_mcp.application.service import AIService
from aplicafacil_mcp.contracts.messages import AiRequestCommand, ProviderConfigChanged, ProviderTestCommand
from aplicafacil_mcp.domain.errors import ConfigNotFoundError
from aplicafacil_mcp.domain.models import ChatMessage, ModelParameters


@pytest.fixture
def wiring(source, publisher):
    EchoProviderAdapter.instances.clear()
    factory = echo_factory()
    registry = ProviderRegistry(factory, source, FAST)
    service = AIService(registry, source, factory, publisher, instance_id="test-1")
    sync = ConfigurationSync(registry, source, publisher, instance_id="test-1")
    return registry, service, sync


def changed(settings, event_type="ai.provider-config.created", version=None, enabled=True) -> ProviderConfigChanged:
    return ProviderConfigChanged(
        schema_version=1,
        event_id=str(uuid.uuid4()),
        event_type=event_type,
        config_id=settings.config_id,
        user_id=settings.user_id,
        version=version or settings.version,
        provider_type=settings.provider_type,
        enabled=enabled,
        correlation_id=str(uuid.uuid4()),
        occurred_at=datetime.now(UTC),
    )


def command(settings, *, deadline_in=60, request_id=None, max_tokens=None) -> AiRequestCommand:
    now = datetime.now(UTC)
    return AiRequestCommand.model_validate(
        {
            "schemaVersion": 1,
            "requestId": request_id or str(uuid.uuid4()),
            "configId": settings.config_id,
            "userId": settings.user_id,
            "correlationId": str(uuid.uuid4()),
            "messages": [{"role": "user", "content": "hola"}],
            "parameters": {"maxTokens": max_tokens} if max_tokens else None,
            "submittedAt": now.isoformat(),
            "deadline": (now + timedelta(seconds=deadline_in)).isoformat(),
        }
    )


# ── eventos de configuración ───────────────────────────────────────────────


async def test_config_event_publishes_initializing_then_active(wiring, source, publisher):
    _, _, sync = wiring
    s = source.add()
    event = changed(s)
    await sync.on_config_changed(event)
    assert [e.status for e in publisher.statuses] == ["INITIALIZING", "ACTIVE"]
    assert all(e.correlation_id == event.correlation_id for e in publisher.statuses)
    assert publisher.statuses[-1].models == ["echo-1", "echo-2"]
    assert publisher.statuses[-1].config_version == 1


async def test_duplicate_config_events_are_idempotent(wiring, source, publisher):
    _, _, sync = wiring
    s = source.add()
    event = changed(s)
    await sync.on_config_changed(event)
    assert await sync.on_config_changed(event) is None
    assert len(publisher.statuses) == 2
    assert len(source.resolve_calls) == 1


async def test_failed_initialization_is_reported_sanitized(wiring, source, publisher):
    _, _, sync = wiring
    s = source.add(api_key="sk-test-bad-" + API_KEY)
    await sync.on_config_changed(changed(s))
    failed = publisher.statuses[-1]
    assert failed.status == "FAILED" and failed.error_code == "AUTHENTICATION_FAILED"
    assert API_KEY not in failed.to_json().decode()


async def test_delete_event_removes_without_publishing_status(wiring, source, publisher):
    registry, _, sync = wiring
    s = source.add()
    await sync.on_config_changed(changed(s))
    source.delete(s.config_id)
    await sync.on_config_changed(changed(s, "ai.provider-config.deleted", version=2, enabled=False))
    assert s.config_id not in registry.loaded_config_ids()
    assert len(publisher.statuses) == 2


async def test_reconcile_recovers_missed_events(wiring, source, publisher):
    registry, _, sync = wiring
    created_while_offline = source.add()
    loaded = source.add()
    await sync.on_config_changed(changed(loaded))
    source.delete(loaded.config_id)  # borrada mientras el servicio estaba apagado
    publisher.statuses.clear()

    changed_count = await sync.reconcile()

    assert changed_count == 2
    assert registry.outcome(created_while_offline.config_id).status == "ACTIVE"
    assert loaded.config_id not in registry.loaded_config_ids()
    assert [e.status for e in publisher.statuses] == ["ACTIVE"]
    assert sync.last_reconciled_ok is True


async def test_reconcile_republishes_status_the_backend_never_got(wiring, source, publisher):
    _, _, sync = wiring
    s = source.add()
    await sync.reconcile()
    publisher.statuses.clear()
    source.configs[s.config_id].status = "PENDING"  # el estado ACTIVE se perdió
    await sync.reconcile()
    assert [e.status for e in publisher.statuses] == ["ACTIVE"]


# ── comandos ───────────────────────────────────────────────────────────────


async def test_request_command_publishes_completed_result(wiring, source, publisher):
    _, service, _ = wiring
    s = source.add()
    cmd = command(s, max_tokens=10)
    event = await service.handle_request_command(cmd)
    assert event.event_type == "ai.request.completed"
    assert (event.request_id, event.correlation_id) == (cmd.request_id, cmd.correlation_id)
    assert event.output.text == "echo: hola"
    assert event.output.usage.output_tokens == 10
    assert publisher.results == [event]


async def test_request_command_is_idempotent(wiring, source, publisher):
    _, service, _ = wiring
    s = source.add()
    cmd = command(s)
    await service.handle_request_command(cmd)
    assert await service.handle_request_command(cmd) is None
    assert len(publisher.results) == 1
    assert EchoProviderAdapter.instances[-1].generate_calls == 1


async def test_expired_request_is_dropped_without_calling_the_provider(wiring, source, publisher):
    _, service, _ = wiring
    s = source.add()
    assert await service.handle_request_command(command(s, deadline_in=-1)) is None
    assert publisher.results == []
    assert EchoProviderAdapter.instances == []


async def test_provider_failure_becomes_a_failed_result(wiring, source, publisher):
    _, service, _ = wiring
    s = source.add(api_key="sk-test-bad-" + API_KEY)
    event = await service.handle_request_command(command(s))
    assert event.event_type == "ai.request.failed"
    assert event.error.code == "AUTHENTICATION_FAILED" and event.error.retryable is False
    assert API_KEY not in event.to_json().decode()


async def test_request_for_someone_elses_config_fails(wiring, source, publisher):
    _, service, _ = wiring
    s = source.add()
    cmd = command(s).model_copy(update={"user_id": str(uuid.uuid4())})
    event = await service.handle_request_command(cmd)
    assert event.error.code == "CONFIG_NOT_FOUND"


async def test_test_command_reports_health(wiring, source, publisher):
    _, service, _ = wiring
    good = source.add()
    bad = source.add(api_key="sk-test-bad-123456")
    for s, expected in ((good, "ACTIVE"), (bad, "FAILED")):
        cmd = ProviderTestCommand(
            schema_version=1,
            command_id=str(uuid.uuid4()),
            config_id=s.config_id,
            user_id=s.user_id,
            config_version=1,
            correlation_id=str(uuid.uuid4()),
            requested_at=datetime.now(UTC),
        )
        event = await service.handle_test_command(cmd)
        assert event.event_type == "ai.provider.test-completed"
        assert event.status == expected and event.correlation_id == cmd.correlation_id


# ── consultas ──────────────────────────────────────────────────────────────


async def test_queries_are_scoped_to_the_user(wiring, source):
    _, service, _ = wiring
    mine = source.add(user_id="ana")
    source.add(user_id="beto")
    views = await service.list_providers("ana")
    assert [v.config.id for v in views] == [mine.config_id]
    assert views[0].capabilities.streaming is False
    with pytest.raises(ConfigNotFoundError):
        await service.get_status("beto", mine.config_id)
    result = await service.generate(
        "ana", mine.config_id, [ChatMessage("user", "x")], parameters=ModelParameters(max_tokens=3)
    )
    assert result.text == "echo: x"


async def test_concurrent_copies_of_a_command_run_once(wiring, source, publisher):
    """RabbitMQ puede entregar dos copias a la vez (prefetch > 1): solo una llama al proveedor."""
    import asyncio

    _, service, _ = wiring
    s = source.add()
    cmd = command(s)
    results = await asyncio.gather(service.handle_request_command(cmd), service.handle_request_command(cmd))
    assert sum(r is not None for r in results) == 1
    assert len(publisher.results) == 1
    assert EchoProviderAdapter.instances[-1].generate_calls == 1


async def test_a_failed_command_can_be_retried(wiring, source, publisher):
    _, service, _ = wiring
    s = source.add()
    cmd = command(s)

    async def broken(_event):
        raise ConnectionError("RabbitMQ caído")

    publisher.publish_result = broken
    with pytest.raises(ConnectionError):
        await service.handle_request_command(cmd)
    del publisher.publish_result  # vuelve al método de la clase
    assert await service.handle_request_command(cmd) is not None
