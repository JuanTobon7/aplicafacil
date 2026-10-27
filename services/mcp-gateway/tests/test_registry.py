"""Registro de adaptadores: versionado, reemplazo atómico, aislamiento de fallos, TTL de credenciales."""

from __future__ import annotations

import pytest
from fakes import FAST, EchoProviderAdapter, FakeSource, echo_factory

from aplicafacil_mcp.application.registry import ProviderRegistry
from aplicafacil_mcp.domain.errors import ConfigDisabledError, ConfigNotFoundError, ProviderError
from aplicafacil_mcp.providers.factory import ProviderFactory


class Clock:
    def __init__(self) -> None:
        self.now = 1000.0

    def __call__(self) -> float:
        return self.now


@pytest.fixture
def clock() -> Clock:
    return Clock()


@pytest.fixture
def registry(source: FakeSource, clock: Clock) -> ProviderRegistry:
    EchoProviderAdapter.instances.clear()
    return ProviderRegistry(echo_factory(), source, FAST, credential_ttl=60, clock=clock)


async def test_apply_initializes_and_reports_models(registry, source):
    s = source.add()
    outcome = await registry.apply(s.config_id, s.user_id, 1, enabled=True)
    assert outcome.status == "ACTIVE"
    assert outcome.models == ("echo-1", "echo-2")
    provider = await registry.get(s.config_id, s.user_id)
    assert provider.is_ready and provider.settings.version == 1
    assert len(source.resolve_calls) == 1  # get() reutiliza el adaptador ya cargado


async def test_repeated_or_older_versions_are_ignored(registry, source):
    s = source.add(version=3)
    assert await registry.apply(s.config_id, s.user_id, 3, enabled=True) is not None
    assert await registry.apply(s.config_id, s.user_id, 3, enabled=True) is None
    assert await registry.apply(s.config_id, s.user_id, 2, enabled=True) is None
    assert len(source.resolve_calls) == 1


async def test_new_version_replaces_and_closes_the_previous_adapter(registry, source):
    s = source.add()
    await registry.apply(s.config_id, s.user_id, 1, enabled=True)
    first = EchoProviderAdapter.instances[-1]
    source.update(s.config_id, model="echo-2")
    await registry.apply(s.config_id, s.user_id, 2, enabled=True)
    second = EchoProviderAdapter.instances[-1]
    assert first is not second
    assert first.closed_calls == 1 and second.closed_calls == 0
    assert (await registry.get(s.config_id, s.user_id)).settings.model == "echo-2"


async def test_failed_rotation_retires_the_old_adapter_without_touching_others(registry, source):
    a = source.add()
    b = source.add()
    await registry.apply(a.config_id, a.user_id, 1, enabled=True)
    await registry.apply(b.config_id, b.user_id, 1, enabled=True)
    old_a = EchoProviderAdapter.instances[0]

    source.update(a.config_id, api_key="sk-test-bad-rotated")
    outcome = await registry.apply(a.config_id, a.user_id, 2, enabled=True)

    assert outcome.status == "FAILED" and outcome.error_code == "AUTHENTICATION_FAILED"
    assert "sk-test-bad-rotated" not in (outcome.error_message or "")
    assert old_a.closed_calls == 1  # la key anterior pudo ser revocada
    assert registry.outcome(b.config_id).status == "ACTIVE"
    assert (await registry.get(b.config_id, b.user_id)).is_ready


async def test_disable_and_delete_remove_the_adapter(registry, source):
    s = source.add()
    await registry.apply(s.config_id, s.user_id, 1, enabled=True)
    adapter = EchoProviderAdapter.instances[-1]
    outcome = await registry.apply(s.config_id, s.user_id, 2, enabled=False)
    assert outcome.status == "REMOVED"
    assert adapter.closed_calls == 1
    assert s.config_id not in registry.loaded_config_ids()


async def test_get_loads_on_demand_and_enforces_ownership(registry, source):
    s = source.add()
    provider = await registry.get(s.config_id, s.user_id)
    assert provider.is_ready
    with pytest.raises(ConfigNotFoundError):
        await registry.get(s.config_id, "otro-usuario")
    with pytest.raises(ConfigNotFoundError):
        await registry.get("no-existe", s.user_id)


async def test_get_rejects_disabled_and_failed_configs(registry, source):
    disabled = source.add(enabled=False)
    with pytest.raises(ConfigDisabledError):
        await registry.get(disabled.config_id, disabled.user_id)
    bad = source.add(api_key="sk-test-bad-123456")
    with pytest.raises(ProviderError) as info:
        await registry.get(bad.config_id, bad.user_id)
    assert info.value.code == "AUTHENTICATION_FAILED"


async def test_credentials_expire_and_are_fetched_again(registry, source, clock):
    s = source.add()
    await registry.get(s.config_id, s.user_id)
    first = EchoProviderAdapter.instances[-1]
    assert len(source.resolve_calls) == 1

    clock.now += 61
    assert await registry.evict_expired() == 1
    assert first.closed_calls == 1  # la credencial salió de memoria
    assert registry.runtime_state(s.config_id)["loaded"] is False

    await registry.get(s.config_id, s.user_id)
    assert len(source.resolve_calls) == 2


async def test_rotation_is_picked_up_after_expiry(registry, source, clock):
    s = source.add()
    await registry.get(s.config_id, s.user_id)
    source.update(s.config_id, api_key="sk-rotated-000000000", version=1)
    clock.now += 61
    provider = await registry.get(s.config_id, s.user_id)
    assert provider.settings.api_key.reveal() == "sk-rotated-000000000"


async def test_unknown_provider_type_fails_only_that_config(source, clock):
    registry = ProviderRegistry(ProviderFactory(), source, FAST, clock=clock)
    s = source.add(provider_type="NOPE")
    outcome = await registry.apply(s.config_id, s.user_id, 1, enabled=True)
    assert outcome.status == "FAILED" and outcome.error_code == "UNKNOWN_PROVIDER_TYPE"


async def test_close_releases_every_adapter(registry, source):
    for _ in range(3):
        s = source.add()
        await registry.apply(s.config_id, s.user_id, 1, enabled=True)
    await registry.close()
    assert all(a.closed_calls == 1 for a in EchoProviderAdapter.instances)
