"""Esquemas de salida de las tools MCP (el SDK publica su JSON Schema como ``outputSchema``).

Son DTO propios: ni objetos de SDK de proveedores ni modelos internos cruzan la frontera MCP.
"""

from __future__ import annotations

from typing import Annotated

from pydantic import BaseModel, Field

UUID_PATTERN = r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
ConfigId = Annotated[str, Field(pattern=UUID_PATTERN, description="id de la configuración (ver list_available_providers)")]


class CapabilitiesOut(BaseModel):
    streaming: bool
    model_listing: bool
    system_messages: bool
    supported_parameters: list[str]


class ProviderOut(BaseModel):
    config_id: str
    display_name: str
    provider_type: str
    model: str
    enabled: bool
    status: str = Field(description="PENDING | INITIALIZING | ACTIVE | FAILED | DISABLED (fuente: backend)")
    capabilities: CapabilitiesOut | None = Field(description="null si este servidor no tiene adaptador para el tipo")


class ProvidersOut(BaseModel):
    providers: list[ProviderOut]


class ModelOut(BaseModel):
    id: str
    owned_by: str | None = None
    context_length: int | None = None


class ModelsOut(BaseModel):
    config_id: str
    models: list[ModelOut]
    truncated: bool


class RuntimeOut(BaseModel):
    loaded: bool
    circuit: str | None = Field(default=None, description="CLOSED | OPEN | HALF_OPEN")
    credential_expires_in_s: int | None = None


class StatusOut(BaseModel):
    config_id: str
    status: str
    enabled: bool
    version: int
    runtime: RuntimeOut | None = Field(description="Estado en esta instancia del mcp-gateway; null si no está cargado")


class UsageOut(BaseModel):
    input_tokens: int | None = None
    output_tokens: int | None = None
    total_tokens: int | None = None


class GenerationOut(BaseModel):
    text: str
    model: str
    provider_type: str
    finish_reason: str | None = None
    usage: UsageOut | None = None
    latency_ms: int | None = None


class ConnectionTestOut(BaseModel):
    healthy: bool
    latency_ms: int | None = None
    error_code: str | None = None
    message: str | None = None
