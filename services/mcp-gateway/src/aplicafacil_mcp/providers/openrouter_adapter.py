"""Adaptador de OpenRouter con su API REST (httpx), sin el SDK de OpenAI.

Diferencias con OpenAI que este adaptador absorbe (y que la orquestación no ve):

- ``GET /models`` es público: no sirve para validar la key. La validación usa ``GET /key``.
- Puede responder 200 con un objeto ``error`` (falla del proveedor de destino): se traduce a ``ProviderError``.
- El streaming SSE incluye comentarios (``: OPENROUTER PROCESSING``) que se ignoran.
- Los modelos se identifican como ``proveedor/modelo`` e informan ``context_length``.
"""

from __future__ import annotations

import json
import time
from collections.abc import AsyncIterator
from typing import Any

import httpx

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


class OpenRouterProviderAdapter(AIProvider):
    provider_type = "OPENROUTER"
    capabilities = ProviderCapabilities(streaming=True, model_listing=True, custom_base_url=False)

    BASE_URL = "https://openrouter.ai/api/v1"
    APP_URL = "https://github.com/aplicafacil"
    APP_TITLE = "AplicaFacil"

    def __init__(self, options: AdapterOptions | None = None, *, transport: httpx.AsyncBaseTransport | None = None):
        super().__init__(options)
        self._transport = transport
        self._http: httpx.AsyncClient | None = None

    async def _open(self, settings: ProviderSettings) -> None:
        self._http = httpx.AsyncClient(
            base_url=self.BASE_URL,
            transport=self._transport,
            timeout=httpx.Timeout(self.options.request_timeout, connect=self.options.connect_timeout),
            headers={
                "Authorization": f"Bearer {settings.api_key.reveal()}",
                "HTTP-Referer": self.APP_URL,
                "X-Title": self.APP_TITLE,
            },
        )

    def _validate(self, settings: ProviderSettings) -> None:
        if "/" not in settings.model:
            raise ConfigurationError(
                "En OpenRouter el modelo se indica como proveedor/modelo (p. ej. openai/gpt-4.1-mini)",
                provider_type=self.provider_type,
            )

    async def _close(self) -> None:
        if self._http is not None:
            await self._http.aclose()
            self._http = None

    async def _list_models(self) -> list[ModelInfo]:
        body = await self._request("GET", "/models")
        models = [
            ModelInfo(id=m["id"], owned_by=m["id"].split("/", 1)[0], context_length=m.get("context_length"))
            for m in body.get("data", [])
            if isinstance(m, dict) and m.get("id")
        ]
        return sorted(models, key=lambda m: m.id)

    async def _generate(self, request: GenerationRequest) -> GenerationResult:
        model = self.effective_model(request)
        body = await self._request("POST", "/chat/completions", json=self._payload(request, model))
        self._raise_embedded_error(body)
        choices = body.get("choices") or []
        if not choices:
            raise ProviderError("El proveedor no devolvió ninguna respuesta", provider_type=self.provider_type)
        choice = choices[0]
        usage = body.get("usage") or None
        return GenerationResult(
            text=(choice.get("message") or {}).get("content") or "",
            model=body.get("model") or model,
            provider_type=self.provider_type,
            finish_reason=choice.get("finish_reason"),
            usage=Usage(usage.get("prompt_tokens"), usage.get("completion_tokens"), usage.get("total_tokens"))
            if usage
            else None,
        )

    async def _stream(self, request: GenerationRequest) -> AsyncIterator[GenerationChunk]:
        model = self.effective_model(request)
        payload = {**self._payload(request, model), "stream": True}
        try:
            async with self._http.stream("POST", "/chat/completions", json=payload) as response:
                if response.status_code >= 400:
                    await response.aread()
                    raise self._status_error(response)
                async for line in response.aiter_lines():
                    if not line.startswith("data:"):
                        continue  # comentarios SSE de OpenRouter y líneas vacías
                    data = line[5:].strip()
                    if data == "[DONE]":
                        break
                    event = json.loads(data)
                    self._raise_embedded_error(event)
                    for choice in event.get("choices") or []:
                        delta = (choice.get("delta") or {}).get("content")
                        if delta or choice.get("finish_reason"):
                            yield GenerationChunk(delta=delta or "", finish_reason=choice.get("finish_reason"))
        except httpx.TimeoutException:
            raise ProviderTimeoutError("El proveedor no respondió a tiempo", provider_type=self.provider_type) from None
        except httpx.TransportError:
            raise ProviderUnavailableError("No se pudo conectar con OpenRouter", provider_type=self.provider_type) from None

    async def health_check(self) -> HealthStatus:
        """Valida la key con ``GET /key`` (``/models`` responde aunque la key sea inválida)."""
        started = time.perf_counter()
        try:
            self._ensure_ready()
            await self._request("GET", "/key")
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
            payload["max_tokens"] = params.max_tokens
        return payload

    async def _request(self, method: str, path: str, **kwargs: Any) -> dict[str, Any]:
        try:
            response = await self._http.request(method, path, **kwargs)
        except httpx.TimeoutException:
            raise ProviderTimeoutError("El proveedor no respondió a tiempo", provider_type=self.provider_type) from None
        except httpx.TransportError:
            raise ProviderUnavailableError("No se pudo conectar con OpenRouter", provider_type=self.provider_type) from None
        if response.status_code >= 400:
            raise self._status_error(response)
        try:
            body = response.json()
        except ValueError:
            raise ProviderError("Respuesta inválida del proveedor", provider_type=self.provider_type) from None
        return body if isinstance(body, dict) else {"data": body}

    def _status_error(self, response: httpx.Response) -> ProviderError:
        detail = None
        try:
            error = response.json().get("error")
            detail = error.get("message") if isinstance(error, dict) else None
        except (ValueError, AttributeError):
            pass
        return error_for_status(
            response.status_code,
            self.provider_type,
            sanitize(detail, self.settings.api_key.reveal()),
            response.headers,
        )

    def _raise_embedded_error(self, body: dict[str, Any]) -> None:
        error = body.get("error")
        if not isinstance(error, dict):
            return
        code = error.get("code")
        status = code if isinstance(code, int) else 502
        raise error_for_status(status, self.provider_type, sanitize(error.get("message"), self.settings.api_key.reveal()))


def _elapsed(started: float) -> int:
    return int((time.perf_counter() - started) * 1000)
