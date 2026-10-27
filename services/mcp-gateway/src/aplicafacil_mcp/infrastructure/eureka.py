"""Registro en Eureka con su API REST (sin librería externa; ver ADR 0005).

- ``POST /apps/{APP}`` al arrancar (cuando el servicio ya está listo) con estado ``UP``.
- ``PUT /apps/{APP}/{instanceId}`` cada ``heartbeat_seconds``; si Eureka responde 404 (lo olvidó tras un
  reinicio o expiró el lease) se vuelve a registrar.
- ``DELETE`` en el apagado ordenado.
- Se anuncia ``instance_ip``/``instance_hostname`` configurables: en contenedores hay que indicar una dirección que
  los demás servicios puedan resolver (no el id del contenedor).
- ``resolve(lb://app)`` devuelve una instancia ``UP`` al azar, para llamar a ai-config-service en la fase desplegada.

El registro no reemplaza a los health checks, a TLS ni a los timeouts de cada llamada.
"""

from __future__ import annotations

import asyncio
import logging
import random
import time

import httpx

from aplicafacil_mcp.config import EurekaSettings
from aplicafacil_mcp.domain.errors import BackendUnavailableError

log = logging.getLogger(__name__)


class EurekaClient:
    def __init__(self, settings: EurekaSettings, http: httpx.AsyncClient, *, port: int, instance_id: str) -> None:
        self._settings = settings
        self._http = http
        self._port = port
        self._base = settings.url.rstrip("/")
        self.app = settings.app_name.upper()
        host = settings.instance_ip if settings.prefer_ip_address else settings.instance_hostname
        self.instance_id = f"{host}:{settings.app_name}:{port}:{instance_id.rsplit(':', 1)[-1]}"
        self._advertised_host = host
        self._task: asyncio.Task[None] | None = None
        self._cache: dict[str, tuple[float, list[str]]] = {}
        self.registered = False

    def instance_payload(self) -> dict:
        base_url = f"http://{self._advertised_host}:{self._port}"
        return {
            "instance": {
                "instanceId": self.instance_id,
                "hostName": self._advertised_host,
                "app": self.app,
                "ipAddr": self._settings.instance_ip,
                "vipAddress": self._settings.app_name,
                "secureVipAddress": self._settings.app_name,
                "status": "UP",
                "port": {"$": self._port, "@enabled": "true"},
                "securePort": {"$": 443, "@enabled": "false"},
                "homePageUrl": f"{base_url}/",
                "statusPageUrl": f"{base_url}/health/live",
                "healthCheckUrl": f"{base_url}/health/ready",
                "dataCenterInfo": {
                    "@class": "com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo",
                    "name": "MyOwn",
                },
                "leaseInfo": {
                    "renewalIntervalInSecs": int(self._settings.heartbeat_seconds),
                    "durationInSecs": self._settings.lease_duration_seconds,
                },
                "metadata": {"management.port": str(self._port), "mcp.path": "/mcp"},
            }
        }

    async def register(self) -> bool:
        try:
            response = await self._http.post(f"{self._base}/apps/{self.app}", json=self.instance_payload())
        except httpx.HTTPError as e:
            log.warning("No se pudo registrar en Eureka", extra={"error": type(e).__name__})
            return False
        self.registered = response.status_code in (200, 204)
        if self.registered:
            log.info("Registrado en Eureka", extra={"app": self.app, "instance_id": self.instance_id})
        else:
            log.warning("Eureka rechazó el registro", extra={"status": response.status_code})
        return self.registered

    async def heartbeat(self) -> bool:
        try:
            response = await self._http.put(f"{self._base}/apps/{self.app}/{self.instance_id}")
        except httpx.HTTPError as e:
            log.warning("Heartbeat de Eureka fallido", extra={"error": type(e).__name__})
            return False
        if response.status_code == 404:
            log.info("Eureka no conoce la instancia: se vuelve a registrar")
            return await self.register()
        return response.status_code in (200, 204)

    async def deregister(self) -> None:
        if not self.registered:
            return
        try:
            await self._http.delete(f"{self._base}/apps/{self.app}/{self.instance_id}")
            log.info("Baja de Eureka", extra={"instance_id": self.instance_id})
        except httpx.HTTPError:
            log.warning("No se pudo dar de baja en Eureka (el lease vencerá solo)")
        self.registered = False

    async def start(self) -> None:
        await self.register()
        self._task = asyncio.create_task(self._loop(), name="eureka-heartbeat")

    async def stop(self) -> None:
        if self._task:
            self._task.cancel()
            try:
                await self._task
            except asyncio.CancelledError:
                pass
        await self.deregister()

    async def _loop(self) -> None:
        while True:
            await asyncio.sleep(self._settings.heartbeat_seconds)
            if self.registered:
                await self.heartbeat()
            else:
                await self.register()

    async def resolve(self, url: str) -> str:
        """``lb://ai-config-service`` → ``http://host:puerto`` (caché de 30 s). Otras URL pasan tal cual."""
        if not url.startswith("lb://"):
            return url
        app = url[5:].split("/", 1)[0].upper()
        cached = self._cache.get(app)
        if cached is None or cached[0] < time.monotonic():
            try:
                response = await self._http.get(f"{self._base}/apps/{app}", headers={"Accept": "application/json"})
                response.raise_for_status()
                instances = response.json()["application"]["instance"]
            except (httpx.HTTPError, KeyError, ValueError):
                raise BackendUnavailableError(f"Eureka no conoce instancias de {app}") from None
            if isinstance(instances, dict):
                instances = [instances]
            urls = [
                f"http://{i.get('ipAddr') if self._settings.prefer_ip_address else i.get('hostName')}:{i['port']['$']}"
                for i in instances
                if i.get("status") == "UP"
            ]
            self._cache[app] = (time.monotonic() + 30, urls)
            cached = self._cache[app]
        if not cached[1]:
            raise BackendUnavailableError(f"No hay instancias UP de {app}")
        return random.choice(cached[1])
