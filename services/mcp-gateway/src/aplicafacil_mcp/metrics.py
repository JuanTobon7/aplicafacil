"""Métricas Prometheus (``GET /metrics``). Etiquetas de baja cardinalidad: nunca userId ni configId."""

from __future__ import annotations

from prometheus_client import CollectorRegistry, Counter, Gauge, Histogram

REGISTRY = CollectorRegistry(auto_describe=True)

PROVIDER_REQUESTS = Counter(
    "mcp_provider_requests_total",
    "Llamadas a proveedores de IA",
    ["provider_type", "operation", "outcome"],
    registry=REGISTRY,
)
PROVIDER_LATENCY = Histogram(
    "mcp_provider_request_seconds",
    "Latencia de las llamadas a proveedores de IA",
    ["provider_type", "operation"],
    buckets=(0.1, 0.25, 0.5, 1, 2, 5, 10, 20, 40, 90),
    registry=REGISTRY,
)
PROVIDER_INITIALIZATIONS = Counter(
    "mcp_provider_initializations_total",
    "Inicializaciones de adaptadores",
    ["provider_type", "outcome"],
    registry=REGISTRY,
)
ACTIVE_PROVIDERS = Gauge(
    "mcp_active_providers",
    "Adaptadores listos en memoria",
    registry=REGISTRY,
)
CIRCUIT_OPEN = Counter(
    "mcp_circuit_open_total",
    "Veces que se abrió un circuito",
    ["provider_type"],
    registry=REGISTRY,
)
MESSAGES_PROCESSED = Counter(
    "mcp_messages_processed_total",
    "Mensajes de RabbitMQ procesados",
    ["message_type", "outcome"],
    registry=REGISTRY,
)
MESSAGE_PROCESSING = Histogram(
    "mcp_message_processing_seconds",
    "Tiempo de procesamiento de mensajes",
    ["message_type"],
    registry=REGISTRY,
)
QUEUE_DEPTH = Gauge(
    "mcp_queue_depth",
    "Mensajes esperando en las colas que consume el mcp-gateway",
    ["queue"],
    registry=REGISTRY,
)
TOOL_CALLS = Counter(
    "mcp_tool_calls_total",
    "Llamadas a tools MCP",
    ["tool", "outcome"],
    registry=REGISTRY,
)
RECONCILIATIONS = Counter(
    "mcp_reconciliations_total",
    "Pasadas de reconciliación con ai-config-service",
    ["outcome"],
    registry=REGISTRY,
)
