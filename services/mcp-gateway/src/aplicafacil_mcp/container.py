"""Raíz de composición: arma el grafo de objetos (inyección de dependencias por constructor).

Es el único lugar que conoce las implementaciones concretas de los puertos. Los tests construyen un
``Container`` con dobles (``source``, ``factory``, ``messaging_enabled=False``…).
"""

from __future__ import annotations

import asyncio
import contextlib
import logging

import httpx
from mcp.server.auth.settings import AuthSettings

from aplicafacil_mcp.application.config_sync import ConfigurationSync
from aplicafacil_mcp.application.ports import ConfigurationSource, EventPublisher, NullPublisher
from aplicafacil_mcp.application.registry import ProviderRegistry
from aplicafacil_mcp.application.service import AIService
from aplicafacil_mcp.config import Settings
from aplicafacil_mcp.contracts.messages import AiRequestResultEvent, ProviderStatusEvent
from aplicafacil_mcp.infrastructure.backend_client import AiConfigClient, ServiceTokenProvider
from aplicafacil_mcp.infrastructure.eureka import EurekaClient
from aplicafacil_mcp.infrastructure.jwt_verifier import IdentityTokenVerifier
from aplicafacil_mcp.infrastructure.messaging import BrokerHandlers, RabbitBroker
from aplicafacil_mcp.mcp_server.server import build_mcp_server
from aplicafacil_mcp.providers.base import AdapterOptions
from aplicafacil_mcp.providers.factory import ProviderFactory

log = logging.getLogger(__name__)


class _PublisherProxy:
    """Rompe el ciclo broker ↔ servicio: el servicio publica por aquí y el broker se conecta después."""

    def __init__(self) -> None:
        self.target: EventPublisher = NullPublisher()

    async def publish_status(self, event: ProviderStatusEvent) -> None:
        await self.target.publish_status(event)

    async def publish_result(self, event: AiRequestResultEvent) -> None:
        await self.target.publish_result(event)


class Container:
    def __init__(
        self,
        settings: Settings,
        *,
        source: ConfigurationSource | None = None,
        factory: ProviderFactory | None = None,
        token_verifier=None,
        broker_factory=RabbitBroker,
    ) -> None:
        self.settings = settings
        self.http = httpx.AsyncClient(timeout=httpx.Timeout(10.0, connect=settings.resilience.connect_timeout))
        self.eureka = (
            EurekaClient(settings.eureka, self.http, port=settings.port, instance_id=settings.instance_id)
            if settings.eureka.enabled
            else None
        )
        tokens = ServiceTokenProvider(
            self.http, settings.identity_service_url, settings.service_client_id, settings.service_client_secret
        )
        self.source: ConfigurationSource = source or AiConfigClient(
            self.http,
            settings.ai_config_service_url,
            tokens,
            **({"resolve_url": self.eureka.resolve} if self.eureka else {}),
        )
        self.factory = factory or ProviderFactory.from_paths(
            settings.adapters,
            AdapterOptions(settings.resilience.connect_timeout, settings.resilience.request_timeout),
        )
        self.registry = ProviderRegistry(
            self.factory, self.source, settings.resilience, credential_ttl=settings.credential_ttl_seconds
        )
        self._publisher = _PublisherProxy()
        self.service = AIService(
            self.registry, self.source, self.factory, self._publisher, instance_id=settings.instance_id
        )
        self.sync = ConfigurationSync(self.registry, self.source, self._publisher, instance_id=settings.instance_id)
        self.broker: RabbitBroker | None = (
            broker_factory(
                settings.rabbitmq_url,
                settings.instance_id,
                BrokerHandlers(
                    on_config_changed=self.sync.on_config_changed,
                    on_test_command=self.service.handle_test_command,
                    on_request_command=self.service.handle_request_command,
                ),
            )
            if settings.messaging_enabled
            else None
        )
        if self.broker is not None:
            self._publisher.target = self.broker
        self.mcp = build_mcp_server(
            self.service,
            generation_timeout=settings.generation_timeout_seconds,
            token_verifier=token_verifier
            or IdentityTokenVerifier(settings.auth_jwks_uri, settings.auth_issuer, settings.auth_audience),
            auth=AuthSettings(
                issuer_url=settings.auth_issuer,
                resource_server_url=settings.resource_url,
                # El verificador ya exige aud = aplicafacil-api (identity-service no emite indicadores RFC 8707)
                validate_token_resource=False,
            ),
        )
        self._stop = asyncio.Event()
        self._tasks: list[asyncio.Task[None]] = []
        self.started = False
        self.stopping = False

    async def start(self) -> None:
        self._stop.clear()
        if self.broker is not None:
            self._tasks.append(asyncio.create_task(self._connect_broker(), name="rabbitmq-connect"))
        # Reconciliación al arrancar y luego periódica: no depende de mensajes que pudieron perderse
        self._tasks.append(
            asyncio.create_task(
                self.sync.run_periodically(self.settings.reconcile_interval_seconds, self._stop), name="reconcile"
            )
        )
        if self.broker is not None:
            self._tasks.append(asyncio.create_task(self._watch_queue_depth(), name="queue-depth"))
        if self.eureka is not None:
            await self.eureka.start()
        self.started = True
        log.info("mcp-gateway iniciado", extra={"instance_id": self.settings.instance_id})

    async def stop(self) -> None:
        """Apagado ordenado: baja de Eureka → dejar de consumir → esperar lo en curso → cerrar adaptadores."""
        self.stopping = True
        if self.eureka is not None:
            await self.eureka.stop()
        self._stop.set()
        if self.broker is not None:
            await self.broker.stop()
        for task in self._tasks:
            task.cancel()
        for task in self._tasks:
            with contextlib.suppress(asyncio.CancelledError, Exception):
                await task
        self._tasks.clear()
        await self.registry.close()
        await self.http.aclose()
        log.info("mcp-gateway detenido")

    def readiness(self) -> dict[str, object]:
        checks: dict[str, object] = {
            "started": self.started and not self.stopping,
            "backend": self.sync.last_reconciled_ok is True,
        }
        if self.broker is not None:
            checks["rabbitmq"] = self.broker.is_connected
        if self.eureka is not None:
            checks["eureka"] = self.eureka.registered
        # Eureka no bloquea la disponibilidad: sin él igual se puede atender
        required = [v for k, v in checks.items() if k != "eureka"]
        return {"ready": all(bool(v) for v in required), "checks": checks}

    async def _connect_broker(self) -> None:
        delay = 1.0
        while not self._stop.is_set():
            try:
                await self.broker.start()
                return
            except Exception as e:  # RabbitMQ puede tardar en levantar
                log.warning("RabbitMQ no disponible; reintentando", extra={"error": type(e).__name__, "delay_s": delay})
                try:
                    await asyncio.wait_for(self._stop.wait(), timeout=delay)
                except TimeoutError:
                    delay = min(delay * 2, 30.0)

    async def _watch_queue_depth(self) -> None:
        while not self._stop.is_set():
            with contextlib.suppress(Exception):
                await self.broker.commands_queue_depth()
            try:
                await asyncio.wait_for(self._stop.wait(), timeout=15)
            except TimeoutError:
                pass


_container: Container | None = None


def set_container(container: Container | None) -> None:
    global _container
    _container = container


def get_container() -> Container:
    if _container is None:
        raise RuntimeError("El contenedor no fue inicializado (ver aplicafacil_mcp.asgi)")
    return _container
