"""Configuración por variables de entorno (las mismas convenciones que infra/.env y config-repo).

Todo lo que no es Django vive aquí, para que el núcleo (proveedores, registro, mensajería) no dependa de
``django.conf.settings`` y se pueda probar sin configurar Django.
"""

from __future__ import annotations

import os
import socket
import uuid
from collections.abc import Mapping
from dataclasses import dataclass, field

DEFAULT_ADAPTERS = {
    "OPENAI": "aplicafacil_mcp.providers.openai_adapter:OpenAIProviderAdapter",
    "OPENROUTER": "aplicafacil_mcp.providers.openrouter_adapter:OpenRouterProviderAdapter",
}


def _bool(value: str | None, default: bool) -> bool:
    if value is None or value.strip() == "":
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def _float(value: str | None, default: float) -> float:
    return default if value is None or value.strip() == "" else float(value)


def _int(value: str | None, default: int) -> int:
    return default if value is None or value.strip() == "" else int(value)


def _list(value: str | None, default: list[str]) -> list[str]:
    if value is None or value.strip() == "":
        return default
    return [item.strip() for item in value.split(",") if item.strip()]


def parse_adapters(value: str | None) -> dict[str, str]:
    """``OPENAI=paquete.modulo:Clase,OTRO=...``. Lo que se indique se suma a (o reemplaza) los adaptadores por defecto."""
    adapters = dict(DEFAULT_ADAPTERS)
    for item in _list(value, []):
        provider_type, _, target = item.partition("=")
        if not target or ":" not in target:
            raise ValueError(f"MCP_PROVIDER_ADAPTERS inválido: {item!r} (formato TIPO=modulo:Clase)")
        adapters[provider_type.strip().upper()] = target.strip()
    return adapters


@dataclass(frozen=True)
class ResilienceSettings:
    connect_timeout: float = 5.0
    request_timeout: float = 60.0
    max_concurrency: int = 4
    rate_per_minute: int = 60
    retry_max_attempts: int = 3
    retry_base_delay: float = 0.5
    retry_max_delay: float = 8.0
    # Generar no es idempotente (cuesta y puede tener efectos): sin reintentos salvo que se pida
    retry_generation: bool = False
    breaker_failure_threshold: int = 5
    breaker_reset_seconds: float = 30.0


@dataclass(frozen=True)
class EurekaSettings:
    enabled: bool = False
    url: str = "http://localhost:8761/eureka/"
    app_name: str = "mcp-gateway"
    instance_hostname: str = field(default_factory=socket.gethostname)
    instance_ip: str = "127.0.0.1"
    prefer_ip_address: bool = True
    heartbeat_seconds: float = 30.0
    lease_duration_seconds: int = 90


@dataclass(frozen=True)
class Settings:
    host: str = "127.0.0.1"
    port: int = 8000
    instance_id: str = field(default_factory=lambda: f"{socket.gethostname()}:{os.getpid()}:{uuid.uuid4().hex[:6]}")
    # URL pública del endpoint MCP (RFC 9728: identificador del recurso)
    resource_url: str = "http://127.0.0.1:8000/mcp"
    allowed_hosts: list[str] = field(default_factory=lambda: ["127.0.0.1:*", "localhost:*"])
    allowed_origins: list[str] = field(default_factory=lambda: ["http://127.0.0.1:*", "http://localhost:*"])

    auth_issuer: str = "http://127.0.0.1:9000"
    auth_jwks_uri: str = "http://127.0.0.1:9000/.well-known/jwks.json"
    auth_audience: str = "aplicafacil-api"

    identity_service_url: str = "http://127.0.0.1:9000"
    service_client_id: str = "mcp-gateway"
    service_client_secret: str = ""
    # http://… directo, o lb://ai-config-service para resolverlo con Eureka (fase desplegada)
    ai_config_service_url: str = "http://127.0.0.1:8086"

    rabbitmq_url: str = "amqp://aplicafacil:aplicafacil@localhost:5672/"
    messaging_enabled: bool = True

    adapters: dict[str, str] = field(default_factory=lambda: dict(DEFAULT_ADAPTERS))
    credential_ttl_seconds: float = 3600.0
    reconcile_interval_seconds: float = 300.0
    generation_timeout_seconds: float = 90.0
    resilience: ResilienceSettings = field(default_factory=ResilienceSettings)
    eureka: EurekaSettings = field(default_factory=EurekaSettings)

    log_level: str = "INFO"
    log_format: str = "json"

    def __repr__(self) -> str:  # nunca imprime secretos
        return f"Settings(instance_id={self.instance_id!r}, port={self.port})"

    @classmethod
    def from_env(cls, env: Mapping[str, str] | None = None) -> Settings:
        e = dict(os.environ if env is None else env)
        host = e.get("MCP_HOST", "127.0.0.1")
        port = _int(e.get("MCP_PORT"), 8000)
        rabbit_url = e.get("RABBITMQ_URL") or "amqp://{user}:{password}@{host}:{port}/{vhost}".format(
            user=e.get("RABBITMQ_USER", "aplicafacil"),
            password=e.get("RABBITMQ_PASSWORD", "aplicafacil"),
            host=e.get("RABBITMQ_HOST", "localhost"),
            port=e.get("RABBITMQ_PORT", "5672"),
            vhost=e.get("RABBITMQ_VHOST", ""),
        )
        issuer = e.get("AUTH_ISSUER_URI", "http://127.0.0.1:9000")
        return cls(
            host=host,
            port=port,
            instance_id=e.get("MCP_INSTANCE_ID") or cls().instance_id,
            resource_url=e.get("MCP_RESOURCE_URL", f"http://127.0.0.1:{port}/mcp"),
            allowed_hosts=_list(e.get("MCP_ALLOWED_HOSTS"), ["127.0.0.1:*", "localhost:*"]),
            allowed_origins=_list(e.get("MCP_ALLOWED_ORIGINS"), ["http://127.0.0.1:*", "http://localhost:*"]),
            auth_issuer=issuer,
            auth_jwks_uri=e.get("AUTH_JWKS_URI", issuer.rstrip("/") + "/.well-known/jwks.json"),
            auth_audience=e.get("AUTH_AUDIENCE", "aplicafacil-api"),
            identity_service_url=e.get("IDENTITY_SERVICE_URL", "http://127.0.0.1:9000"),
            service_client_id=e.get("SERVICE_CLIENT_ID", "mcp-gateway"),
            service_client_secret=e.get("SERVICE_CLIENT_SECRET", ""),
            ai_config_service_url=e.get("AI_CONFIG_SERVICE_URL", "http://127.0.0.1:8086"),
            rabbitmq_url=rabbit_url,
            messaging_enabled=_bool(e.get("MCP_MESSAGING_ENABLED"), True),
            adapters=parse_adapters(e.get("MCP_PROVIDER_ADAPTERS")),
            credential_ttl_seconds=_float(e.get("MCP_CREDENTIAL_TTL_SECONDS"), 3600.0),
            reconcile_interval_seconds=_float(e.get("MCP_RECONCILE_INTERVAL_SECONDS"), 300.0),
            generation_timeout_seconds=_float(e.get("MCP_GENERATION_TIMEOUT_SECONDS"), 90.0),
            resilience=ResilienceSettings(
                connect_timeout=_float(e.get("MCP_CONNECT_TIMEOUT_SECONDS"), 5.0),
                request_timeout=_float(e.get("MCP_REQUEST_TIMEOUT_SECONDS"), 60.0),
                max_concurrency=_int(e.get("MCP_PROVIDER_MAX_CONCURRENCY"), 4),
                rate_per_minute=_int(e.get("MCP_PROVIDER_RATE_PER_MINUTE"), 60),
                retry_max_attempts=_int(e.get("MCP_RETRY_MAX_ATTEMPTS"), 3),
                retry_generation=_bool(e.get("MCP_RETRY_GENERATION"), False),
                breaker_failure_threshold=_int(e.get("MCP_BREAKER_FAILURE_THRESHOLD"), 5),
                breaker_reset_seconds=_float(e.get("MCP_BREAKER_RESET_SECONDS"), 30.0),
            ),
            eureka=EurekaSettings(
                enabled=_bool(e.get("EUREKA_ENABLED"), False),
                url=e.get("EUREKA_URL", "http://localhost:8761/eureka/"),
                app_name=e.get("EUREKA_APP_NAME", "mcp-gateway"),
                instance_hostname=e.get("EUREKA_INSTANCE_HOSTNAME") or socket.gethostname(),
                instance_ip=e.get("EUREKA_INSTANCE_IP", "127.0.0.1"),
                prefer_ip_address=_bool(e.get("EUREKA_PREFER_IP_ADDRESS"), True),
                heartbeat_seconds=_float(e.get("EUREKA_HEARTBEAT_SECONDS"), 30.0),
                lease_duration_seconds=_int(e.get("EUREKA_LEASE_DURATION_SECONDS"), 90),
            ),
            log_level=e.get("LOG_LEVEL", "INFO").upper(),
            log_format=e.get("LOG_FORMAT", "json"),
        )
