"""Cliente de ai-config-service (contracts/openapi/ai-config-internal.yaml) con token de servicio.

- El token se pide a identity-service (``POST /internal/token``, HTTP Basic con ``SERVICE_CLIENT_SECRET``) y se
  guarda en memoria hasta 30 s antes de vencer, como ``ServiceTokenProvider`` de linkedin-service.
- La URL del backend puede ser ``lb://ai-config-service``: se resuelve con Eureka en cada llamada.
- Lecturas idempotentes: reintento acotado ante errores de red o 5xx.
- La respuesta con la API key se convierte en ``ProviderSettings`` con ``Secret`` y nunca se registra.
"""

from __future__ import annotations

import asyncio
import logging
import random
import time
from collections.abc import Awaitable, Callable
from datetime import datetime

import httpx

from aplicafacil_mcp.domain.errors import BackendUnavailableError, ConfigNotFoundError
from aplicafacil_mcp.domain.models import ConfigSummary, ModelParameters, ProviderSettings, Secret

log = logging.getLogger(__name__)

UrlResolver = Callable[[str], Awaitable[str]]


async def _static(url: str) -> str:
    return url


class ServiceTokenProvider:
    def __init__(self, http: httpx.AsyncClient, identity_url: str, client_id: str, client_secret: str) -> None:
        self._http = http
        self._url = identity_url.rstrip("/") + "/internal/token"
        self._client_id = client_id
        self._secret = client_secret
        self._token: str | None = None
        self._expires_at = 0.0
        self._lock = asyncio.Lock()

    def __repr__(self) -> str:
        return f"ServiceTokenProvider(client_id={self._client_id!r})"

    async def token(self) -> str:
        async with self._lock:
            if self._token and self._expires_at - 30 > time.time():
                return self._token
            try:
                response = await self._http.post(self._url, auth=(self._client_id, self._secret))
            except httpx.HTTPError:
                raise BackendUnavailableError("identity-service no responde") from None
            if response.status_code != 200:
                raise BackendUnavailableError(f"identity-service rechazó el token de servicio ({response.status_code})")
            body = response.json()
            self._token = body["accessToken"]
            expires = body["expiresAt"]
            self._expires_at = (
                float(expires)
                if isinstance(expires, (int, float))
                else datetime.fromisoformat(expires.replace("Z", "+00:00")).timestamp()
            )
            return self._token

    def invalidate(self) -> None:
        self._token = None


class AiConfigClient:
    """Implementa ``ConfigurationSource``."""

    def __init__(
        self,
        http: httpx.AsyncClient,
        base_url: str,
        tokens: ServiceTokenProvider,
        *,
        resolve_url: UrlResolver = _static,
        attempts: int = 3,
    ) -> None:
        self._http = http
        self._base_url = base_url
        self._tokens = tokens
        self._resolve_url = resolve_url
        self._attempts = attempts

    async def list_configs(self, user_id: str | None = None) -> list[ConfigSummary]:
        params = {"userId": user_id} if user_id else None
        body = await self._get("/internal/provider-configs", params)
        return [
            ConfigSummary(
                id=c["id"],
                user_id=c["userId"],
                provider_type=c["providerType"],
                display_name=c["displayName"],
                enabled=c["enabled"],
                model=c["model"],
                version=int(c["version"]),
                status=c["status"],
                available_models=tuple(c.get("availableModels") or ()),
            )
            for c in body
        ]

    async def resolve(self, config_id: str, user_id: str) -> ProviderSettings:
        body = await self._get(f"/internal/provider-configs/{config_id}", {"userId": user_id})
        params = body.get("parameters") or {}
        try:
            return ProviderSettings(
                config_id=body["id"],
                user_id=body["userId"],
                provider_type=body["providerType"],
                display_name=body["displayName"],
                model=body["model"],
                version=int(body["version"]),
                api_key=Secret(body.pop("apiKey")),
                base_url=body.get("baseUrl"),
                parameters=ModelParameters(params.get("temperature"), params.get("maxTokens"), params.get("topP")),
                enabled=bool(body["enabled"]),
            )
        finally:
            body.clear()

    async def _get(self, path: str, params: dict[str, str] | None):
        last_error: str = "sin respuesta"
        for attempt in range(1, self._attempts + 1):
            try:
                base = (await self._resolve_url(self._base_url)).rstrip("/")
                token = await self._tokens.token()
                response = await self._http.get(
                    base + path, params=params, headers={"Authorization": f"Bearer {token}"}
                )
            except httpx.HTTPError as e:
                last_error = type(e).__name__
            except BackendUnavailableError as e:
                last_error = e.message
            else:
                if response.status_code == 404:
                    raise ConfigNotFoundError("La configuración no existe")
                if response.status_code == 401:
                    self._tokens.invalidate()
                    last_error = "401"
                elif response.status_code < 400:
                    return response.json()
                elif response.status_code < 500:
                    raise BackendUnavailableError(f"ai-config-service respondió {response.status_code}")
                else:
                    last_error = str(response.status_code)
            if attempt < self._attempts:
                await asyncio.sleep(random.uniform(0, 0.2 * 2**attempt))
        log.warning("ai-config-service no disponible", extra={"path": path.split("?")[0], "error": last_error})
        raise BackendUnavailableError("ai-config-service no responde")
