"""Compatibilidad de mensajes con contracts/schemas (los mismos JSON Schema que usa el backend Java).

- Lo que publica el mcp-gateway cumple el schema.
- Lo que publica ai-config-service (ejemplos con la forma exacta de los records de AiMessages.java) cumple el schema
  y el mcp-gateway lo lee.
- Ningún schema ni modelo tiene un campo para credenciales.
"""

from __future__ import annotations

import json
import uuid
from datetime import UTC, datetime, timedelta

import pytest
from conftest import SCHEMAS_DIR
from jsonschema import Draft202012Validator, FormatChecker

from aplicafacil_mcp.contracts.messages import (
    AiRequestCommand,
    AiRequestResultEvent,
    ErrorDto,
    OutputDto,
    ProviderConfigChanged,
    ProviderStatusEvent,
    ProviderTestCommand,
    UsageDto,
)


def validator(name: str) -> Draft202012Validator:
    schema = json.loads((SCHEMAS_DIR / f"{name}.schema.json").read_text(encoding="utf-8"))
    return Draft202012Validator(schema, format_checker=FormatChecker())


def ids() -> dict[str, str]:
    return {k: str(uuid.uuid4()) for k in ("event", "config", "user", "corr", "req", "cmd")}


# ── salientes (mcp-gateway → ai-config-service) ─────────────────────────────


@pytest.mark.parametrize("status", ["INITIALIZING", "ACTIVE", "FAILED"])
def test_status_events_match_schema(status):
    i = ids()
    event = ProviderStatusEvent(
        event_type="ai.provider.initialization-failed" if status == "FAILED" else "ai.provider.initialization-succeeded",
        config_id=i["config"],
        user_id=i["user"],
        config_version=3,
        status=status,
        error_code="AUTHENTICATION_FAILED" if status == "FAILED" else None,
        error_message="La API key no es válida" if status == "FAILED" else None,
        models=["a", "b"] if status == "ACTIVE" else None,
        latency_ms=12,
        correlation_id=i["corr"],
        instance_id="host:1:abc",
    )
    validator("ai-provider-status-event").validate(json.loads(event.to_json()))


def test_result_events_match_schema():
    i = ids()
    completed = AiRequestResultEvent(
        event_type="ai.request.completed",
        request_id=i["req"],
        config_id=i["config"],
        user_id=i["user"],
        correlation_id=i["corr"],
        output=OutputDto(text="hola", model="m", finish_reason="stop", usage=UsageDto(input_tokens=1, output_tokens=2)),
        latency_ms=5,
    )
    failed = AiRequestResultEvent(
        event_type="ai.request.failed",
        request_id=i["req"],
        config_id=i["config"],
        user_id=i["user"],
        correlation_id=i["corr"],
        error=ErrorDto(code="RATE_LIMITED", message="espera", retryable=True),
    )
    for event in (completed, failed):
        validator("ai-request-result-event").validate(json.loads(event.to_json()))


# ── entrantes (ai-config-service → mcp-gateway), forma de AiMessages.java ─


def java_config_changed() -> dict:
    i = ids()
    return {
        "schemaVersion": 1,
        "eventId": i["event"],
        "eventType": "ai.provider-config.credential-rotated",
        "configId": i["config"],
        "userId": i["user"],
        "version": 4,
        "providerType": "OPENROUTER",
        "enabled": True,
        "correlationId": i["corr"],
        "occurredAt": "2026-10-15T20:00:00.123456Z",
    }


def java_request_command() -> dict:
    i = ids()
    now = datetime.now(UTC)
    return {
        "schemaVersion": 1,
        "requestId": i["req"],
        "configId": i["config"],
        "userId": i["user"],
        "correlationId": i["corr"],
        "messages": [{"role": "system", "content": "Breve"}, {"role": "user", "content": "Hola"}],
        "parameters": {"model": None, "temperature": 0.2, "maxTokens": 256, "topP": None},
        "submittedAt": now.isoformat().replace("+00:00", "Z"),
        "deadline": (now + timedelta(minutes=2)).isoformat().replace("+00:00", "Z"),
    }


def java_test_command() -> dict:
    i = ids()
    return {
        "schemaVersion": 1,
        "commandId": i["cmd"],
        "configId": i["config"],
        "userId": i["user"],
        "configVersion": 2,
        "correlationId": i["corr"],
        "requestedAt": "2026-10-15T20:00:00Z",
    }


@pytest.mark.parametrize(
    ("schema", "payload", "model"),
    [
        ("ai-provider-config-changed-event", java_config_changed(), ProviderConfigChanged),
        ("ai-request-command", java_request_command(), AiRequestCommand),
        ("ai-provider-test-command", java_test_command(), ProviderTestCommand),
    ],
    ids=["config-changed", "request-command", "test-command"],
)
def test_backend_messages_match_schema_and_parse(schema, payload, model):
    validator(schema).validate(payload)
    parsed = model.model_validate_json(json.dumps(payload))
    assert parsed.config_id == payload["configId"]


def test_unknown_fields_from_a_newer_producer_are_tolerated():
    payload = {**java_config_changed(), "someFutureField": 1}
    assert ProviderConfigChanged.model_validate(payload).version == 4


def test_invalid_command_is_rejected():
    payload = java_request_command()
    payload["messages"] = []
    with pytest.raises(ValueError):
        AiRequestCommand.model_validate(payload)


def test_no_schema_or_model_has_a_credential_field():
    forbidden = ("apikey", "api_key", "secret", "password", "credential", "token")
    for path in SCHEMAS_DIR.glob("ai-*.schema.json"):
        properties = json.dumps(json.loads(path.read_text(encoding="utf-8"))["properties"]).lower()
        assert not any(f'"{word}' in properties for word in forbidden), path.name
    for model in (ProviderConfigChanged, ProviderStatusEvent, AiRequestCommand, AiRequestResultEvent, ProviderTestCommand):
        fields = " ".join(model.model_fields).lower()
        assert not any(word in fields for word in forbidden), model.__name__
