"""Punto de entrada ASGI (``uvicorn aplicafacil_mcp.asgi:application``).

Django no implementa MCP: este despachador mínimo envía ``/mcp`` y la metadata RFC 9728 a la app Streamable HTTP
del SDK oficial y todo lo demás a Django. También maneja el ciclo de vida (``lifespan``): arranca el gestor de
sesiones MCP y el contenedor (RabbitMQ, reconciliación, Eureka) y los apaga en orden.
"""

from __future__ import annotations

import contextlib
import logging
import os

from django.core.asgi import get_asgi_application
from mcp.server.transport_security import TransportSecuritySettings

from aplicafacil_mcp.config import Settings
from aplicafacil_mcp.container import Container, set_container
from aplicafacil_mcp.logging_setup import configure_logging

log = logging.getLogger(__name__)

MCP_PREFIXES = ("/mcp", "/.well-known/oauth-protected-resource")


class GatewayASGI:
    def __init__(self, django_app, container: Container) -> None:
        self.django = django_app
        self.container = container
        settings = container.settings
        self.mcp = container.mcp.streamable_http_app(
            streamable_http_path="/mcp",
            transport_security=TransportSecuritySettings(
                enable_dns_rebinding_protection=True,
                allowed_hosts=settings.allowed_hosts,
                allowed_origins=settings.allowed_origins,
            ),
            host=settings.host,
        )
        self._stack: contextlib.AsyncExitStack | None = None

    async def __call__(self, scope, receive, send) -> None:
        if scope["type"] == "lifespan":
            await self._lifespan(receive, send)
            return
        path = scope.get("path", "")
        if any(path == p or path.startswith(p + "/") for p in MCP_PREFIXES):
            await self.mcp(scope, receive, send)
        else:
            await self.django(scope, receive, send)

    async def _lifespan(self, receive, send) -> None:
        while True:
            message = await receive()
            if message["type"] == "lifespan.startup":
                try:
                    self._stack = contextlib.AsyncExitStack()
                    await self._stack.enter_async_context(self.container.mcp.session_manager.run())
                    await self.container.start()
                except Exception as e:
                    log.exception("No se pudo iniciar el mcp-gateway")
                    await send({"type": "lifespan.startup.failed", "message": str(e)})
                    return
                await send({"type": "lifespan.startup.complete"})
            elif message["type"] == "lifespan.shutdown":
                try:
                    await self.container.stop()
                    if self._stack is not None:
                        await self._stack.aclose()
                finally:
                    await send({"type": "lifespan.shutdown.complete"})
                return


def create_application(settings: Settings | None = None, container: Container | None = None) -> GatewayASGI:
    os.environ.setdefault("DJANGO_SETTINGS_MODULE", "aplicafacil_mcp.settings")
    settings = settings or Settings.from_env()
    configure_logging(settings.log_level, settings.log_format)
    django_app = get_asgi_application()
    container = container or Container(settings)
    set_container(container)
    return GatewayASGI(django_app, container)


def __getattr__(name: str):
    # ``application`` se crea al primer acceso (uvicorn), no al importar el módulo (tests)
    if name == "application":
        app = create_application()
        globals()["application"] = app
        return app
    raise AttributeError(name)
