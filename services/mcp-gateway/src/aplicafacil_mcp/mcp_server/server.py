"""Tools MCP de AplicaFacil. Alcance acotado: solo las configuraciones de IA del usuario del token.

No hay tools genéricas (HTTP arbitrario, acceso libre al backend ni a credenciales). Cada tool:
1. obtiene el usuario del token (``sub``), nunca de un argumento;
2. valida la entrada (pydantic, publicado como ``inputSchema``);
3. delega en ``AIService`` y traduce ``AppError`` a ``ToolError`` con un código estable.
"""

from __future__ import annotations

import asyncio
import logging
from collections.abc import Awaitable, Callable
from typing import Annotated, TypeVar

from mcp.server.auth.middleware.auth_context import get_access_token
from mcp.server.auth.settings import AuthSettings
from mcp.server.mcpserver import Context, MCPServer
from mcp.server.mcpserver.exceptions import ToolError
from mcp.types import ToolAnnotations
from pydantic import Field

from aplicafacil_mcp import __version__, metrics
from aplicafacil_mcp.application.service import AIService, ProviderView
from aplicafacil_mcp.domain.errors import AppError
from aplicafacil_mcp.domain.models import ChatMessage, ModelParameters
from aplicafacil_mcp.mcp_server.schemas import (
    CapabilitiesOut,
    ConfigId,
    ConnectionTestOut,
    GenerationOut,
    ModelOut,
    ModelsOut,
    ProviderOut,
    ProvidersOut,
    RuntimeOut,
    StatusOut,
    UsageOut,
)

log = logging.getLogger(__name__)
T = TypeVar("T")

MAX_MODELS = 200
INSTRUCTIONS = (
    "Herramientas para usar los proveedores de IA que la persona configuró en AplicaFacil. "
    "Empieza con list_available_providers para conocer los config_id. Nunca se exponen API keys."
)


def current_user_id() -> str:
    token = get_access_token()
    if token is None or not token.subject:
        raise ToolError("ACCESS_DENIED: se necesita un token de usuario de AplicaFacil")
    return token.subject


def build_mcp_server(
    service: AIService,
    *,
    generation_timeout: float,
    token_verifier=None,
    auth: AuthSettings | None = None,
    principal: Callable[[], str] = current_user_id,
) -> MCPServer:
    mcp = MCPServer(
        name="aplicafacil",
        title="AplicaFacil",
        instructions=INSTRUCTIONS,
        version=__version__,
        token_verifier=token_verifier,
        auth=auth,
    )
    read_only = ToolAnnotations(readOnlyHint=True, openWorldHint=False)

    async def run(tool: str, action: Callable[[], Awaitable[T]]) -> T:
        try:
            result = await action()
        except AppError as e:
            metrics.TOOL_CALLS.labels(tool, e.code).inc()
            raise ToolError(f"{e.code}: {e.message}") from None
        except TimeoutError:
            metrics.TOOL_CALLS.labels(tool, "TIMEOUT").inc()
            raise ToolError("TIMEOUT: el proveedor no respondió a tiempo") from None
        metrics.TOOL_CALLS.labels(tool, "ok").inc()
        return result

    @mcp.tool(
        name="list_available_providers",
        description="Lista las configuraciones de IA de la persona con su estado y capacidades.",
        annotations=read_only,
    )
    async def list_available_providers() -> ProvidersOut:
        user_id = principal()
        views = await run("list_available_providers", lambda: service.list_providers(user_id))
        return ProvidersOut(providers=[_provider_out(v) for v in views])

    @mcp.tool(
        name="list_available_models",
        description="Modelos disponibles con la credencial de una configuración.",
        annotations=read_only,
    )
    async def list_available_models(config_id: ConfigId) -> ModelsOut:
        user_id = principal()
        models = await run("list_available_models", lambda: service.list_models(user_id, config_id))
        return ModelsOut(
            config_id=config_id,
            models=[ModelOut(id=m.id, owned_by=m.owned_by, context_length=m.context_length) for m in models[:MAX_MODELS]],
            truncated=len(models) > MAX_MODELS,
        )

    @mcp.tool(
        name="get_provider_status",
        description="Estado de una configuración (backend) y de su adaptador en este servidor.",
        annotations=read_only,
    )
    async def get_provider_status(config_id: ConfigId) -> StatusOut:
        user_id = principal()
        view = await run("get_provider_status", lambda: service.get_status(user_id, config_id))
        runtime = view.runtime
        return StatusOut(
            config_id=view.config.id,
            status=view.config.status,
            enabled=view.config.enabled,
            version=view.config.version,
            runtime=RuntimeOut(
                loaded=bool(runtime["loaded"]),
                circuit=runtime.get("circuit"),
                credential_expires_in_s=runtime.get("credential_expires_in_s"),
            )
            if runtime
            else None,
        )

    @mcp.tool(
        name="generate_ai_response",
        description=(
            "Genera una respuesta con el proveedor y modelo de una configuración. Los parámetros omitidos usan "
            "los valores guardados en la configuración."
        ),
        annotations=ToolAnnotations(readOnlyHint=False, destructiveHint=False, idempotentHint=False, openWorldHint=True),
    )
    async def generate_ai_response(
        config_id: ConfigId,
        prompt: Annotated[str, Field(min_length=1, max_length=32000)],
        ctx: Context,
        system_prompt: Annotated[str | None, Field(max_length=8000)] = None,
        model: Annotated[str | None, Field(max_length=200, description="Otro modelo del mismo proveedor")] = None,
        temperature: Annotated[float | None, Field(ge=0, le=2)] = None,
        max_tokens: Annotated[int | None, Field(ge=1, le=200000)] = None,
        top_p: Annotated[float | None, Field(ge=0, le=1)] = None,
    ) -> GenerationOut:
        user_id = principal()
        messages = ([ChatMessage("system", system_prompt)] if system_prompt else []) + [ChatMessage("user", prompt)]
        parameters = ModelParameters(temperature, max_tokens, top_p)
        await ctx.report_progress(0, 1, "Enviando al proveedor")

        async def generate():
            async with asyncio.timeout(generation_timeout):
                return await service.generate(user_id, config_id, messages, model=model, parameters=parameters)

        result = await run("generate_ai_response", generate)
        await ctx.report_progress(1, 1, "Respuesta recibida")
        usage = result.usage
        return GenerationOut(
            text=result.text,
            model=result.model,
            provider_type=result.provider_type,
            finish_reason=result.finish_reason,
            usage=UsageOut(
                input_tokens=usage.input_tokens, output_tokens=usage.output_tokens, total_tokens=usage.total_tokens
            )
            if usage
            else None,
            latency_ms=result.latency_ms,
        )

    @mcp.tool(
        name="test_provider_connection",
        description="Verifica que la credencial de una configuración funciona (no genera texto).",
        annotations=ToolAnnotations(readOnlyHint=True, openWorldHint=True),
    )
    async def test_provider_connection(config_id: ConfigId) -> ConnectionTestOut:
        user_id = principal()
        health = await run("test_provider_connection", lambda: service.test_connection(user_id, config_id))
        return ConnectionTestOut(
            healthy=health.healthy, latency_ms=health.latency_ms, error_code=health.error_code, message=health.message
        )

    return mcp


def _provider_out(view: ProviderView) -> ProviderOut:
    caps = view.capabilities
    return ProviderOut(
        config_id=view.config.id,
        display_name=view.config.display_name,
        provider_type=view.config.provider_type,
        model=view.config.model,
        enabled=view.config.enabled,
        status=view.config.status,
        capabilities=CapabilitiesOut(
            streaming=caps.streaming,
            model_listing=caps.model_listing,
            system_messages=caps.system_messages,
            supported_parameters=sorted(caps.supported_parameters),
        )
        if caps
        else None,
    )
