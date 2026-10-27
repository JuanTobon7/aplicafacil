"""Errores con semántica común para todos los adaptadores (parte del contrato de ``AIProvider``).

Cada adaptador traduce los errores de su SDK a esta jerarquía; ningún error de un SDK escapa de un adaptador.
``code`` es estable (viaja en los eventos y lo muestra el front) y ``retryable`` dice si repetir puede servir.
Los mensajes nunca incluyen credenciales ni headers (ver ``application.sanitize``).
"""

from __future__ import annotations


class AppError(Exception):
    code = "INTERNAL_ERROR"
    retryable = False

    def __init__(self, message: str) -> None:
        super().__init__(message)
        self.message = message


# ── Errores de proveedor ──────────────────────────────────────────────────


class ProviderError(AppError):
    code = "PROVIDER_ERROR"

    def __init__(self, message: str, *, provider_type: str | None = None, status_code: int | None = None) -> None:
        super().__init__(message)
        self.provider_type = provider_type
        self.status_code = status_code


class ConfigurationError(ProviderError):
    """La configuración es inválida para este adaptador (falta algo, URL no permitida…). Sin llamar a la red."""

    code = "INVALID_CONFIGURATION"


class AuthenticationError(ProviderError):
    """La API key no es válida, fue revocada o no tiene permiso (401/403)."""

    code = "AUTHENTICATION_FAILED"


class RateLimitError(ProviderError):
    code = "RATE_LIMITED"
    retryable = True

    def __init__(self, message: str, *, retry_after: float | None = None, **kwargs) -> None:
        super().__init__(message, **kwargs)
        self.retry_after = retry_after


class ProviderUnavailableError(ProviderError):
    """5xx o error de conexión."""

    code = "PROVIDER_UNAVAILABLE"
    retryable = True


class ProviderTimeoutError(ProviderError):
    code = "TIMEOUT"
    retryable = True


class InvalidRequestError(ProviderError):
    """El proveedor rechazó la solicitud (400/422): parámetros o contenido inválidos."""

    code = "INVALID_REQUEST"


class ModelNotFoundError(ProviderError):
    code = "MODEL_NOT_FOUND"


class UnsupportedCapabilityError(ProviderError):
    """El adaptador no soporta la operación (p. ej. streaming): se dice explícitamente, nunca se simula."""

    code = "UNSUPPORTED_CAPABILITY"


class ProviderNotInitializedError(ProviderError):
    code = "NOT_INITIALIZED"


class CircuitOpenError(ProviderError):
    """El circuito de esta configuración está abierto tras fallos seguidos: se rechaza sin llamar al proveedor."""

    code = "CIRCUIT_OPEN"
    retryable = True


class ConcurrencyLimitError(ProviderError):
    code = "CONCURRENCY_LIMIT"
    retryable = True


# ── Errores de la aplicación ──────────────────────────────────────────────


class ConfigNotFoundError(AppError):
    """No existe o no pertenece a quien pregunta (no se distingue, para no filtrar información)."""

    code = "CONFIG_NOT_FOUND"


class ConfigDisabledError(AppError):
    code = "CONFIG_DISABLED"


class UnknownProviderTypeError(AppError):
    code = "UNKNOWN_PROVIDER_TYPE"


class BackendUnavailableError(AppError):
    code = "BACKEND_UNAVAILABLE"
    retryable = True


class AccessDeniedError(AppError):
    code = "ACCESS_DENIED"
