"""Health (liveness), readiness y métricas. Públicas: no exponen datos de usuarios ni configuraciones."""

from django.http import HttpRequest, HttpResponse, JsonResponse
from django.views.decorators.http import require_GET
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest

from aplicafacil_mcp import __version__
from aplicafacil_mcp.container import get_container
from aplicafacil_mcp.metrics import REGISTRY


@require_GET
async def index(request: HttpRequest) -> JsonResponse:
    return JsonResponse({"service": "mcp-gateway", "version": __version__, "mcp": "/mcp"})


@require_GET
async def live(request: HttpRequest) -> JsonResponse:
    """El proceso responde. No depende de RabbitMQ ni del backend (reiniciar no los arregla)."""
    return JsonResponse({"status": "UP"})


@require_GET
async def ready(request: HttpRequest) -> JsonResponse:
    """Listo para recibir tráfico: RabbitMQ conectado y ai-config-service alcanzable en la última reconciliación."""
    state = get_container().readiness()
    return JsonResponse(
        {"status": "UP" if state["ready"] else "DOWN", "checks": state["checks"]},
        status=200 if state["ready"] else 503,
    )


@require_GET
async def metrics(request: HttpRequest) -> HttpResponse:
    return HttpResponse(generate_latest(REGISTRY), content_type=CONTENT_TYPE_LATEST)
