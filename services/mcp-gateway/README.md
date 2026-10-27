# mcp-gateway (Python · Django · MCP)

Servidor MCP que deja usar, desde clientes MCP, los proveedores de IA que cada persona configuró en AplicaFacil.
Decisión y alternativas: [ADR 0005](../../docs/adr/0005-mcp-gateway-y-proveedores-de-ia.md).

```
 Front /ia ──HTTPS+JWT──▶ api-gateway ──▶ ai-config-service (Java, :8086)        fuente de verdad
                                            │  provider_config, API keys cifradas (AES-256-GCM), outbox
                                            │
                     aplicafacil.events ◀───┤ ai.provider-config.*  (sin secretos)
                     aplicafacil.commands ◀─┘ ai.provider.test, ai.request.submit
                              │
                              ▼
 Cliente MCP ──JWT──▶ mcp-gateway (:8000)
                       ├─ /mcp ............ SDK oficial `mcp` (Streamable HTTP)  ─┐
                       ├─ /health/* /metrics  Django                              ├─▶ AIService ─▶ ProviderRegistry ─▶ ProviderFactory
                       ├─ RabbitMQ (aio-pika): consumidores + publicador          │                  │                  ├ OpenAIProviderAdapter     (SDK openai)
                       ├─ Reconciliación periódica (GET /internal/provider-configs)┘                  │                  └ OpenRouterProviderAdapter (httpx)
                       └─ Eureka (cliente REST propio; solo fase desplegada)              ResilientProvider: timeout, reintentos, breaker, límites
```

| Capa | Paquete | Depende de |
|---|---|---|
| Dominio | `domain/` (modelos, errores, saneo) | nada |
| Contrato de proveedor | `providers/base.py` (`AIProvider`) | dominio |
| Adaptadores | `providers/openai_adapter.py`, `openrouter_adapter.py` | **su SDK** + dominio |
| Orquestación | `application/` (`AIService`, `ProviderRegistry`, `ConfigurationSync`, puertos) | abstracciones |
| Mensajes | `contracts/messages.py` (espejo de `contracts/schemas`) | pydantic |
| Infraestructura | `infrastructure/` (backend, RabbitMQ, Eureka, JWT) | httpx, aio-pika, pyjwt |
| Transporte MCP | `mcp_server/` | SDK `mcp` + `AIService` |
| Django | `web/`, `settings.py` | solo health/ready/métricas |
| Composición | `container.py`, `asgi.py` | todo (único lugar con implementaciones concretas) |

Django **no** implementa MCP: `asgi.py` envía `/mcp` y `/.well-known/oauth-protected-resource` a la app del SDK y
lo demás a Django. No hay base de datos ni migraciones: la configuración vive en `ai-config-service`.

## Correr en local

`dev-up.ps1` lo prepara y lo arranca junto con el resto (instala `uv` si falta y crea `.venv` con Python 3.13);
`.\dev-up.cmd -Test` corre todas sus pruebas, incluidas las de RabbitMQ real. A mano:

```bash
cd services/mcp-gateway
uv sync
uv run python -m aplicafacil_mcp
```

Variables: [`.env.example`](.env.example). Las mínimas en local son `SERVICE_CLIENT_SECRET` (el de `infra/.env`) y
las de RabbitMQ. Sin RabbitMQ: `MCP_MESSAGING_ENABLED=false` (las tools siguen funcionando; no hay eventos).

| Endpoint | Para qué |
|---|---|
| `POST/GET /mcp` | MCP Streamable HTTP. Exige `Authorization: Bearer <JWT de usuario de identity-service>` |
| `GET /.well-known/oauth-protected-resource/mcp` | Metadata RFC 9728 |
| `GET /health/live` | Liveness (el proceso responde) |
| `GET /health/ready` | Readiness: RabbitMQ conectado y ai-config-service alcanzable en la última reconciliación (503 si no) |
| `GET /metrics` | Prometheus |

## Tests

```bash
uv run pytest                     # todo; las pruebas con RabbitMQ real se omiten si Docker no responde
uv run pytest -m "not integration"
MCP_TEST_RABBITMQ_URL=amqp://user:pass@127.0.0.1:5672/vhost uv run pytest -m integration   # broker existente
```

Ninguna prueba necesita API keys reales: los adaptadores se prueban contra un upstream HTTP simulado
(`tests/fakes.py`).

| Archivo | Qué verifica |
|---|---|
| `test_provider_contract.py` | La **misma** suite para OpenAI, OpenRouter y `ECHO` (proveedor definido solo en los tests): ciclo de vida, errores, normalización, capacidades, que la key nunca aparezca |
| `test_registry.py` | Versionado idempotente, reemplazo atómico, fallo aislado, TTL y rotación de credenciales |
| `test_resilience.py` | Reintentos solo en operaciones idempotentes, breaker, límites, timeouts |
| `test_service.py` | Eventos de configuración, reconciliación, comandos idempotentes, vencimiento |
| `test_message_contracts.py` | Lo que entra y sale cumple `contracts/schemas/*.json`; ningún mensaje tiene campo para credenciales |
| `test_mcp_tools.py` | Tools con el cliente MCP oficial en proceso: esquemas, alcance por usuario, errores |
| `test_http_app.py` | Servicio real con uvicorn: JWT (firma, `aud`, `iss`, expiración, rol), DNS rebinding, health, métricas |
| `test_infrastructure.py` | Eureka (registro, heartbeat, re-registro, baja, `lb://`) y cliente del backend (token de servicio, reintentos) |
| `test_rabbitmq_integration.py` | RabbitMQ real: evento → estado; comando → resultado correlacionado; reentrega ignorada; mensaje inválido → DLQ |

## Tools MCP

Todas operan sobre el usuario del token (`sub`); ninguna recibe un `userId`.

| Tool | Entrada | Salida |
|---|---|---|
| `list_available_providers` | — | configuraciones con estado y capacidades |
| `list_available_models` | `config_id` | modelos (máx. 200) |
| `get_provider_status` | `config_id` | estado del backend y del adaptador en esta instancia (breaker, credencial) |
| `generate_ai_response` | `config_id`, `prompt`, `system_prompt?`, `model?`, `temperature?`, `max_tokens?`, `top_p?` | texto normalizado, modelo, uso, latencia |
| `test_provider_connection` | `config_id` | `healthy`, latencia, código de error |

Errores: `is_error=true` con `CODIGO: mensaje` (`CONFIG_NOT_FOUND`, `AUTHENTICATION_FAILED`, `RATE_LIMITED`,
`PROVIDER_UNAVAILABLE`, `CIRCUIT_OPEN`, `TIMEOUT`, …). No hay `stream_ai_response`: una tool MCP devuelve su resultado
en una sola respuesta; `generate_ai_response` envía notificaciones de progreso al empezar y al terminar.

Ejemplo (después de `initialize`):

```json
{"jsonrpc":"2.0","id":2,"method":"tools/call",
 "params":{"name":"generate_ai_response","arguments":{"config_id":"7c0e…","prompt":"Resume este aviso","max_tokens":200}}}
```

## Eventos y comandos

Contratos: [`contracts/events/asyncapi.yaml`](../../contracts/events/asyncapi.yaml) y `contracts/schemas/ai-*.schema.json`.
Ningún mensaje lleva una API key.

| Cola | Tipo | Recibe |
|---|---|---|
| `mcp-gateway.provider-config.<instancia>` | exclusiva, temporal (una por instancia) | `ai.provider-config.*` |
| `mcp-gateway.commands` (+ `.dlq`) | durable, compartida | `ai.provider.test`, `ai.request.submit` |

Publica en `aplicafacil.events`: `ai.provider.initialization-{started,succeeded,failed}`, `ai.provider.test-completed`,
`ai.request.{completed,failed}`. Ejemplo saneado:

```json
{"schemaVersion":1,"eventId":"5b1d…","eventType":"ai.provider.initialization-failed","configId":"7c0e…",
 "userId":"a9f2…","configVersion":3,"status":"FAILED","errorCode":"AUTHENTICATION_FAILED",
 "errorMessage":"La API key no es válida o fue revocada.","latencyMs":412,"correlationId":"e01c…",
 "occurredAt":"2026-10-16T14:02:11Z","instanceId":"host:8123:4fa1c2"}
```

Garantías:

- **Idempotencia:** cambios con versión ≤ a la conocida se ignoran; `requestId`/`commandId` ya procesados se ignoran
  (memoria acotada por instancia; el backend además descarta resultados de solicitudes ya cerradas).
- **Fallos:** mensaje que no cumple el contrato → DLQ sin reintentos. Error transitorio → 3 intentos con backoff y
  jitter → DLQ. Se confirma (ack) solo después de procesar.
- **Mensajes perdidos:** la reconciliación (al arrancar y cada `MCP_RECONCILE_INTERVAL_SECONDS`) compara versiones con
  el backend, carga lo que falte, retira lo borrado y vuelve a informar estados que el backend no recibió.
- **Solicitudes vencidas:** si `deadline` pasó, no se llama al proveedor (el backend ya la cerró como `TIMED_OUT`).

### Inspeccionar y recuperar la DLQ

```bash
# Cuántos mensajes hay y verlos (UI: http://127.0.0.1:15672 → Queues → mcp-gateway.commands.dlq → Get messages)
docker compose -f infra/docker-compose.yml exec rabbitmq rabbitmqctl list_queues name messages | grep dlq
# Reenviarlos a la cola original una vez corregida la causa (plugin shovel o "Move messages" en la UI)
```

## Credenciales

- Llegan solo por `GET /internal/provider-configs/{id}?userId=…` (token de servicio con `sub=mcp-gateway`; el backend
  verifica que la configuración sea de ese usuario).
- Viven en memoria dentro del adaptador como `Secret` (su `repr` es `****`) y vencen a los
  `MCP_CREDENTIAL_TTL_SECONDS`: el adaptador se cierra y la key se vuelve a pedir al usarla.
- Rotar o desactivar: el evento llega, el adaptador nuevo se verifica antes de cerrar el anterior; si la key nueva
  falla, el anterior también se retira (su key pudo ser revocada) sin tocar otras configuraciones.
- Logs: `RedactingFilter` pasa mensajes, campos y trazas por `domain/sanitize.py` (keys `sk-…`, `Bearer …`, JWT,
  `api_key=…`). `httpx` se silencia a WARNING porque registraría URLs con `userId`.

## Agregar un proveedor

1. Crear `providers/<nombre>_adapter.py` con una clase que herede de `AIProvider`, declare `provider_type` y
   `capabilities`, e implemente `_open`, `_close`, `_list_models`, `_generate`, `health_check` (y `_stream` si
   `streaming=True`). Traducir **todos** los errores del SDK con `providers/http_errors.py`.
2. Registrarla: agregarla a `DEFAULT_ADAPTERS` en `config.py` o, sin tocar código, con
   `MCP_PROVIDER_ADAPTERS=NUEVO=paquete.modulo:Clase`.
3. Agregar el tipo en `services/backend/config-repo/ai-config-service.yml` (`aplicafacil.ai.provider-types`).
4. Agregar un `Harness` en `tests/test_provider_contract.py` y que pase la suite.

`AIService`, `ProviderRegistry`, las tools y la mensajería no cambian (el proveedor `ECHO` de los tests lo demuestra).

## Despliegue

- Imagen: `docker build -t aplicafacil/mcp-gateway services/mcp-gateway` (usuario sin privilegios, `uv sync --frozen`).
- Compose: `docker compose -f infra/docker-compose.yml --profile apps up -d` agrega `ai-config-service` y `mcp-gateway`.
- Eureka (`EUREKA_ENABLED=true`): se registra como `MCP-GATEWAY` con heartbeat cada 30 s, lease de 90 s y baja en el
  apagado. Anunciar una dirección que los demás resuelvan: `EUREKA_INSTANCE_HOSTNAME` (nombre del servicio en
  Compose/Kubernetes) con `EUREKA_PREFER_IP_ADDRESS=false`, o `EUREKA_INSTANCE_IP`. `AI_CONFIG_SERVICE_URL=lb://ai-config-service`
  resuelve el backend con Eureka.
- Escalado horizontal: cada instancia tiene su cola exclusiva de cambios y comparte `mcp-gateway.commands`; las
  sesiones MCP son por instancia, así que el balanceador debe mantener afinidad por `Mcp-Session-Id`.
- Apagado ordenado (SIGTERM): baja de Eureka → deja de consumir → espera lo que está en curso (hasta 30 s) →
  cierra adaptadores y conexiones.
- Fase desplegada: la API interna transporta la key en claro entre servicios; exige TLS/mTLS (ADR 0005).

## Problemas frecuentes

| Síntoma | Causa probable |
|---|---|
| `/health/ready` 503 con `backend: false` | ai-config-service caído o `SERVICE_CLIENT_SECRET` distinto al de identity-service (`identity-service` debe listar `mcp-gateway` en `service-clients`) |
| `/health/ready` 503 con `rabbitmq: false` | RabbitMQ no levantó; el servicio reintenta solo con backoff |
| `/mcp` responde 401 | Token ausente, vencido, de servicio (se exigen tokens de usuario) o con `iss`/`aud` distintos a `AUTH_ISSUER_URI`/`AUTH_AUDIENCE` |
| `/mcp` responde 421/403 | Host u Origin no permitido (`MCP_ALLOWED_HOSTS`, `MCP_ALLOWED_ORIGINS`) |
| Configuración queda en `PENDING` | El mcp-gateway no está corriendo o no recibe eventos; al arrancar, la reconciliación la inicializa |
| `CIRCUIT_OPEN` | El proveedor falló `MCP_BREAKER_FAILURE_THRESHOLD` veces seguidas; reintenta tras `MCP_BREAKER_RESET_SECONDS` |
| `CREDENTIAL_UNREADABLE` | Cambió `SESSION_ENCRYPTION_KEY`; volver a ingresar la API key |

## Limitaciones

- Sin token exchange: un cliente MCP necesita un token de usuario de AplicaFacil (identity-service no implementa
  RFC 8693). `automation-service` todavía no puede usar estas tools en nombre de un usuario.
- La idempotencia de comandos es por instancia (más el filtro del backend): dos instancias podrían ejecutar el mismo
  `ai.request.submit` reentregado; el segundo resultado se descarta, pero la llamada al proveedor ocurre.
- La reconciliación carga las configuraciones activas de todos los usuarios en cada instancia (adecuado para la fase
  local; en la desplegada conviene particionar).
- Sin tracing distribuido: el proyecto no tiene stack de tracing; se propaga `correlationId` en mensajes y logs.
