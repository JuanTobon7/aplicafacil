"""Sincroniza el registro con ai-config-service: eventos ``ai.provider-config.*`` y reconciliación periódica.

La reconciliación no depende de los mensajes: al arrancar (y cada ``interval``) compara las versiones del backend
con las conocidas y corrige lo que se haya perdido mientras el servicio estaba apagado o la cola exclusiva no
existía.
"""

from __future__ import annotations

import asyncio
import logging

from aplicafacil_mcp import metrics
from aplicafacil_mcp.application.ports import ConfigurationSource, EventPublisher
from aplicafacil_mcp.application.registry import InitOutcome, ProviderRegistry
from aplicafacil_mcp.contracts.messages import ProviderConfigChanged, ProviderStatusEvent, new_id
from aplicafacil_mcp.domain.errors import AppError

log = logging.getLogger(__name__)


class ConfigurationSync:
    def __init__(
        self,
        registry: ProviderRegistry,
        source: ConfigurationSource,
        publisher: EventPublisher,
        *,
        instance_id: str,
    ) -> None:
        self._registry = registry
        self._source = source
        self._publisher = publisher
        self._instance_id = instance_id
        self.last_reconciled_ok: bool | None = None

    async def on_config_changed(self, event: ProviderConfigChanged) -> InitOutcome | None:
        """Idempotente: una versión ya aplicada (o anterior) no hace nada."""
        if not self._registry.is_newer(event.config_id, event.version):
            log.debug("Cambio repetido o atrasado", extra={"config_id": event.config_id, "version": event.version})
            return None
        if not event.removes:
            await self._publish_started(event.config_id, event.user_id, event.version, event.correlation_id)
        outcome = await self._registry.apply(event.config_id, event.user_id, event.version, enabled=not event.removes)
        if outcome is not None:
            await self._publish_outcome(outcome, event.correlation_id)
        return outcome

    async def reconcile(self) -> int:
        """@return cuántas configuraciones cambió."""
        try:
            configs = await self._source.list_configs()
        except AppError as e:
            self.last_reconciled_ok = False
            metrics.RECONCILIATIONS.labels("failed").inc()
            log.warning("No se pudo reconciliar", extra={"error_code": e.code})
            return 0
        changed = 0
        backend_ids = set()
        for config in configs:
            backend_ids.add(config.id)
            if self._registry.is_newer(config.id, config.version):
                outcome = await self._registry.apply(config.id, config.user_id, config.version, enabled=config.enabled)
                if outcome is not None:
                    changed += 1
                    await self._publish_outcome(outcome, new_id())
            elif config.enabled and config.status in ("PENDING", "INITIALIZING"):
                # El backend no recibió el estado (evento perdido): se vuelve a informar
                outcome = self._registry.outcome(config.id)
                if outcome is not None and outcome.version == config.version:
                    await self._publish_outcome(outcome, new_id())
        for config_id in self._registry.loaded_config_ids() - backend_ids:
            await self._registry.remove(config_id)
            changed += 1
        evicted = await self._registry.evict_expired()
        self.last_reconciled_ok = True
        metrics.RECONCILIATIONS.labels("ok").inc()
        log.info("Reconciliación completa", extra={"changed": changed, "evicted": evicted, "configs": len(configs)})
        return changed

    async def run_periodically(self, interval: float, stop: asyncio.Event) -> None:
        while not stop.is_set():
            await self.reconcile()
            try:
                await asyncio.wait_for(stop.wait(), timeout=interval)
            except TimeoutError:
                pass

    async def _publish_started(self, config_id: str, user_id: str, version: int, correlation_id: str) -> None:
        await self._safe_publish(
            ProviderStatusEvent(
                event_type="ai.provider.initialization-started",
                config_id=config_id,
                user_id=user_id,
                config_version=version,
                status="INITIALIZING",
                correlation_id=correlation_id,
                instance_id=self._instance_id,
            )
        )

    async def _publish_outcome(self, outcome: InitOutcome, correlation_id: str) -> None:
        if outcome.status == "REMOVED":
            return
        await self._safe_publish(
            ProviderStatusEvent(
                event_type="ai.provider.initialization-succeeded"
                if outcome.status == "ACTIVE"
                else "ai.provider.initialization-failed",
                config_id=outcome.config_id,
                user_id=outcome.user_id,
                config_version=outcome.version,
                status=outcome.status,
                error_code=outcome.error_code,
                error_message=outcome.error_message,
                models=list(outcome.models) if outcome.status == "ACTIVE" else None,
                latency_ms=outcome.latency_ms,
                correlation_id=correlation_id,
                instance_id=self._instance_id,
            )
        )

    async def _safe_publish(self, event: ProviderStatusEvent) -> None:
        try:
            await self._publisher.publish_status(event)
        except Exception:  # la reconciliación lo vuelve a informar
            log.exception("No se pudo publicar el estado", extra={"config_id": event.config_id})
