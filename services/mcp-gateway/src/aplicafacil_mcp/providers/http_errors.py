"""Traducción común de respuestas HTTP de error a la jerarquía ``ProviderError`` (contrato, punto 3)."""

from __future__ import annotations

from collections.abc import Mapping

from aplicafacil_mcp.domain.errors import (
    AuthenticationError,
    InvalidRequestError,
    ModelNotFoundError,
    ProviderError,
    ProviderUnavailableError,
    RateLimitError,
)

_GENERIC = {
    401: "La API key no es válida o fue revocada.",
    403: "La API key no tiene permiso para esta operación.",
    404: "El modelo o recurso no existe para esta cuenta.",
    429: "El proveedor limitó las solicitudes (rate limit o cuota agotada).",
}


def error_for_status(
    status: int,
    provider_type: str,
    detail: str | None = None,
    headers: Mapping[str, str] | None = None,
) -> ProviderError:
    """``detail`` debe venir ya saneado; si falta, se usa un mensaje genérico."""
    message = (detail or _GENERIC.get(status) or f"El proveedor respondió {status}.")[:300]
    kwargs = {"provider_type": provider_type, "status_code": status}
    if status in (401, 403):
        return AuthenticationError(_GENERIC[status], **kwargs)
    if status == 429:
        return RateLimitError(message, retry_after=_retry_after(headers), **kwargs)
    if status == 404:
        return ModelNotFoundError(message, **kwargs)
    if status in (400, 409, 413, 422):
        return InvalidRequestError(message, **kwargs)
    if status >= 500 or status == 408:
        return ProviderUnavailableError(message, **kwargs)
    return ProviderError(message, **kwargs)


def _retry_after(headers: Mapping[str, str] | None) -> float | None:
    if not headers:
        return None
    value = headers.get("retry-after") or headers.get("Retry-After")
    try:
        return float(value) if value is not None else None
    except ValueError:
        return None
