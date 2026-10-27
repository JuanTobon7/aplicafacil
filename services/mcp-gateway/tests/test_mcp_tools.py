"""Tools MCP con el cliente oficial en proceso: esquemas, alcance por usuario y errores."""

from __future__ import annotations

import pytest
from fakes import FAST, echo_factory
from mcp import Client

from aplicafacil_mcp.application.registry import ProviderRegistry
from aplicafacil_mcp.application.service import AIService
from aplicafacil_mcp.mcp_server.server import build_mcp_server

EXPECTED_TOOLS = {
    "list_available_providers",
    "list_available_models",
    "get_provider_status",
    "generate_ai_response",
    "test_provider_connection",
}


@pytest.fixture
def user():
    return {"id": "ana"}


@pytest.fixture
def server(source, publisher, user):
    factory = echo_factory()
    registry = ProviderRegistry(factory, source, FAST)
    service = AIService(registry, source, factory, publisher, instance_id="t")
    return build_mcp_server(service, generation_timeout=5, principal=lambda: user["id"])


async def test_exposes_only_the_scoped_tools_with_schemas(server):
    async with Client(server) as client:
        tools = {t.name: t for t in (await client.list_tools()).tools}
    assert set(tools) == EXPECTED_TOOLS
    generate = tools["generate_ai_response"]
    props = generate.input_schema["properties"]
    assert "user_id" not in props and "userId" not in props  # el usuario sale del token
    assert props["temperature"]["anyOf"][0]["maximum"] == 2
    assert "config_id" in generate.input_schema["required"]
    assert generate.output_schema["properties"]["text"]["type"] == "string"
    assert tools["list_available_providers"].annotations.read_only_hint is True


async def test_list_and_generate_with_own_configuration(server, source):
    mine = source.add(user_id="ana")
    source.add(user_id="beto")
    async with Client(server) as client:
        listed = await client.call_tool("list_available_providers", {})
        assert not listed.is_error
        providers = listed.structured_content["providers"]
        assert [p["config_id"] for p in providers] == [mine.config_id]
        assert providers[0]["capabilities"]["streaming"] is False

        generated = await client.call_tool(
            "generate_ai_response", {"config_id": mine.config_id, "prompt": "hola", "max_tokens": 5}
        )
        assert not generated.is_error
        assert generated.structured_content["text"] == "echo: hola"
        assert generated.structured_content["usage"]["output_tokens"] == 5

        models = await client.call_tool("list_available_models", {"config_id": mine.config_id})
        assert [m["id"] for m in models.structured_content["models"]] == ["echo-1", "echo-2"]

        status = await client.call_tool("get_provider_status", {"config_id": mine.config_id})
        assert status.structured_content["runtime"]["loaded"] is True

        health = await client.call_tool("test_provider_connection", {"config_id": mine.config_id})
        assert health.structured_content["healthy"] is True


async def test_another_users_configuration_is_not_reachable(server, source):
    others = source.add(user_id="beto")
    async with Client(server) as client:
        for tool, args in [
            ("generate_ai_response", {"config_id": others.config_id, "prompt": "x"}),
            ("list_available_models", {"config_id": others.config_id}),
            ("get_provider_status", {"config_id": others.config_id}),
        ]:
            result = await client.call_tool(tool, args)
            assert result.is_error
            assert "CONFIG_NOT_FOUND" in result.content[0].text


async def test_invalid_input_is_rejected(server, source):
    mine = source.add(user_id="ana")
    async with Client(server) as client:
        bad_id = await client.call_tool("generate_ai_response", {"config_id": "not-a-uuid", "prompt": "x"})
        too_hot = await client.call_tool(
            "generate_ai_response", {"config_id": mine.config_id, "prompt": "x", "temperature": 5}
        )
        empty = await client.call_tool("generate_ai_response", {"config_id": mine.config_id, "prompt": ""})
    assert bad_id.is_error and too_hot.is_error and empty.is_error


async def test_provider_errors_are_reported_with_stable_codes(server, source):
    bad = source.add(user_id="ana", api_key="sk-test-bad-0000000")
    async with Client(server) as client:
        result = await client.call_tool("generate_ai_response", {"config_id": bad.config_id, "prompt": "x"})
        health = await client.call_tool("test_provider_connection", {"config_id": bad.config_id})
    assert result.is_error and "AUTHENTICATION_FAILED" in result.content[0].text
    assert "sk-test-bad" not in result.content[0].text
    assert health.structured_content["healthy"] is False
