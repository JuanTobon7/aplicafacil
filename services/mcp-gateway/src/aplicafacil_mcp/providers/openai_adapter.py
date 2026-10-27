"""Adaptador de OpenAI (y APIs compatibles con OpenAI: Ollama, LM Studio, vLLM…) con el SDK oficial ``openai``.

El SDK solo se importa en este módulo. Hacia afuera solo salen tipos de ``domain.models`` y ``ProviderError``.
"""

from __future__ import annotations

import time
from collections.abc import AsyncIterator
from typing import Any

import httpx2
import openai

from aplicafacil_mcp.domain.errors import (
    ConfigurationError,
    ProviderError,
    ProviderTimeoutError,
    ProviderUnavailableError,
)
from aplicafacil_mcp.domain.models import (
    GenerationChunk,
    GenerationRequest,
    GenerationResult,
    HealthStatus,
    ModelInfo,
    ProviderCapabilities,
    ProviderSettings,
    Usage,
)
from aplicafacil_mcp.domain.sanitize import sanitize
from aplicafacil_mcp.providers.base import AdapterOptions, AIProvider
from aplicafacil_mcp.providers.http_errors import error_for_status


class OpenAIProviderAdapter(AIProvider):
    provider_type = "OPENAI"
    capabilities = ProviderCapabilities(streaming=True, model_listing=True, custom_base_url=True)

    DEFAULT_BASE_URL = "https://api.openai.com/v1"

    def __init__(self, options: AdapterOptions | None = None, *, transport: httpx2.AsyncBaseTransport | None = None):
        super().__init__(options)
        self._transport = transport
        self._client: openai.AsyncOpenAI | None = None

    async def _open(self, settings: ProviderSettings) -> None:
        timeout = httpx2.Timeout(self.options.request_timeout, connect=self.options.connect_timeout)
        http_client = httpx2.AsyncClient(transport=self._transport, timeout=timeout) if self._transport else None
        self._client = openai.AsyncOpenAI(
            api_key=settings.api_key.reveal(),
            base_url=settings.base_url or self.DEFAULT_BASE_URL,
            timeout=timeout,
            # Los reintentos los decide ResilientProvider (y nunca para generar, salvo política explícita)
            max_retries=0,
            http_client=http_client,
        )

    def _validate(self, settings: ProviderSettings) -> None:
        if len(settings.api_key.reveal()) < 8:
            raise ConfigurationError("La API key es demasiado corta", provider_type=self.provider_type)

    async def _close(self) -> None:
        if self._client is not None:
            await self._client.close()
            self._client = None

    async def _list_models(self) -> list[ModelInfo]:
        try:
            page = await self._client.models.list()
        except openai.OpenAIError as e:
            raise self._translate(e) from None
        return sorted((ModelInfo(id=m.id, owned_by=getattr(m, "owned_by", None)) for m in page.data), key=lambda m: m.id)

    async def _generate(self, request: GenerationRequest) -> GenerationResult:
        model = self.effective_model(request)
        try:
            response = await self._client.chat.completions.create(**self._payload(request, model))
        except openai.OpenAIError as e:
            raise self._translate(e) from None
        if not response.choices:
            raise ProviderError("El proveedor no devolvió ninguna respuesta", provider_type=self.provider_type)
        choice = response.choices[0]
        usage = response.usage
        return GenerationResult(
            text=choice.message.content or "",
            model=response.model or model,
            provider_type=self.provider_type,
            finish_reason=choice.finish_reason,
            usage=Usage(usage.prompt_tokens, usage.completion_tokens, usage.total_tokens) if usage else None,
        )

    async def _stream(self, request: GenerationRequest) -> AsyncIterator[GenerationChunk]:
        model = self.effective_model(request)
        try:
            stream = await self._client.chat.completions.create(**self._payload(request, model), stream=True)
        except openai.OpenAIError as e:
            raise self._translate(e) from None
        try:
            async for chunk in stream:
                if not chunk.choices:
                    continue
                choice = chunk.choices[0]
                delta = choice.delta.content if choice.delta else None
                if delta or choice.finish_reason:
                    yield GenerationChunk(delta=delta or "", finish_reason=choice.finish_reason)
        except openai.OpenAIError as e:
            raise self._translate(e) from None
        finally:
            await stream.close()

    async def health_check(self) -> HealthStatus:
        started = time.perf_counter()
        try:
            await self.list_models()
        except ProviderError as e:
            return HealthStatus(False, _elapsed(started), e.code, e.message)
        return HealthStatus(True, _elapsed(started))

    def _payload(self, request: GenerationRequest, model: str) -> dict[str, Any]:
        params = self.effective_parameters(request)
        payload: dict[str, Any] = {
            "model": model,
            "messages": [{"role": m.role, "content": m.content} for m in request.messages],
        }
        if params.temperature is not None:
            payload["temperature"] = params.temperature
        if params.top_p is not None:
            payload["top_p"] = params.top_p
        if params.max_tokens is not None:
            # La API oficial usa max_completion_tokens (obligatorio en modelos de razonamiento);
            # las compatibles (Ollama, vLLM…) siguen esperando max_tokens
            key = "max_tokens" if self.settings.base_url else "max_completion_tokens"
            payload[key] = params.max_tokens
        return payload

    def _translate(self, error: openai.OpenAIError) -> ProviderError:
        secret = self.settings.api_key.reveal()
        if isinstance(error, openai.APITimeoutError):
            return ProviderTimeoutError("El proveedor no respondió a tiempo", provider_type=self.provider_type)
        if isinstance(error, openai.APIConnectionError):
            return ProviderUnavailableError("No se pudo conectar con el proveedor", provider_type=self.provider_type)
        if isinstance(error, openai.APIStatusError):
            headers = dict(error.response.headers) if error.response is not None else None
            return error_for_status(error.status_code, self.provider_type, sanitize(_detail(error), secret), headers)
        return ProviderError(sanitize(str(error), secret) or "Error del proveedor", provider_type=self.provider_type)


def _detail(error: openai.APIStatusError) -> str | None:
    body = error.body
    if isinstance(body, dict):
        inner = body.get("error", body)
        if isinstance(inner, dict) and inner.get("message"):
            return str(inner["message"])
    return error.message


def _elapsed(started: float) -> int:
    return int((time.perf_counter() - started) * 1000)
