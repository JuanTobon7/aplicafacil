# ADR 0005 — mcp-gateway (Django + MCP) y configuración de proveedores de IA

- **Estado:** propuesta
- **Fecha:** 2026-10-15
- **Decisores:** Juan Tobón
- **Relacionada con:** [ADR 0001](0001-arquitectura-microservicios.md) (mcp-gateway en Python),
  [ADR 0003](0003-identidad-linkedin-y-sesion-local.md) (identidad y tokens de servicio),
  [ADR 0004](0004-scraper-como-implementacion-del-navegador.md) (RabbitMQ sin secretos)

## Contexto

ADR 0001 reservó un `mcp-gateway` en Python para exponer las capacidades del backend como tools MCP; hasta ahora
solo existía su README. Además, cada usuario necesita configurar sus propios proveedores de IA (OpenAI,
OpenRouter…) con su API key, elegir modelo y parámetros, y ver si la configuración funciona.

### Lo que ya existe y se reutiliza

| Pieza | Dónde | Cómo se usa aquí |
|---|---|---|
| RabbitMQ 4.3 | `infra/docker-compose.yml` | Exchanges `aplicafacil.events` y `aplicafacil.commands` (topic) y DLX `aplicafacil.dlx`, igual que automation-service |
| Transactional outbox | `identity-service` | Copiado en `ai-config-service`: el evento se guarda en la misma transacción que el cambio |
| JWT propios + JWKS | `identity-service` | El mcp-gateway valida los tokens de usuario (`aud = aplicafacil-api`) con el JWKS |
| Tokens de servicio | `identity-service` `/internal/token` | `mcp-gateway` y `ai-config-service` se agregan a `service-clients` |
| Cifrado en reposo | `libs/platform-crypto` (AES-256-GCM) | Las API keys se guardan cifradas con `SESSION_ENCRYPTION_KEY` y `key_version` |
| Eureka / config-server | `platform/*` | Solo en la fase desplegada; en local se desactivan (perfil `local`), igual que los demás servicios |
| Front React + gateway con token local | `apps/web`, `api-gateway` | Nueva página `/ia`; ruta `/api/ai/**` |

Lo que **no** existía: un dueño de la configuración de IA, credenciales de proveedores de IA, el código del
mcp-gateway y cualquier cliente de Eureka en Python. El README anterior del mcp-gateway mencionaba un token
exchange (RFC 8693) contra `auth-service`; ese servicio pasó a `legacy/` y `identity-service` no implementa token
exchange (ver "Limitaciones").

## Decisión

### 1. `ai-config-service` (Java, nuevo, :8086, schema `aiconfig`)

Fuente de verdad de las configuraciones de proveedores. Dueño de `provider_config`, `provider_credential`,
`ai_request` y su outbox. Se eligió un servicio nuevo en lugar de meterlo en `automation-service` (que no tiene BD) o
en `identity-service` (identidad no debe conocer proveedores de IA): un dueño claro para las credenciales, como
`linkedin-service` lo es de la sesión.

- La API key se recibe una sola vez (alta o rotación), se cifra y **nunca** vuelve al front: este solo ve una pista
  (`••••abcd`).
- Cada cambio incrementa `version` y publica, por el outbox, un evento **sin secretos** (`ai.provider-config.*`).
- El estado (`PENDING`, `INITIALIZING`, `ACTIVE`, `FAILED`, `DISABLED`) lo decide el backend con los eventos que
  publica el mcp-gateway; un evento de una versión vieja se ignora.
- Los tipos de proveedor válidos se leen de configuración (`aplicafacil.ai.provider-types` en config-repo).

### 2. `mcp-gateway` (Python 3.13, Django 6 + SDK oficial `mcp` 2.x, :8000)

```
           ┌──────────────── ASGI (uvicorn) ────────────────┐
 /mcp ───▶ │ MCP Streamable HTTP (mcp.server.MCPServer)     │──┐
           │   TokenVerifier: JWT de identity-service       │  │  AIService (orquestación)
 /health ─▶│ Django (vistas de health, readiness, métricas) │  ├─▶ ProviderRegistry ─▶ ProviderFactory
           └────────────────────────────────────────────────┘  │      │                 ├ OpenAIProviderAdapter (SDK openai)
   RabbitMQ (aio-pika) ── consumidores de eventos y comandos ──┘      │                 └ OpenRouterProviderAdapter (httpx)
   Eureka (cliente REST propio) · ai-config-service (/internal)       └ ResilientProvider (timeouts, reintentos, breaker, límites)
```

- Django **no** implementa MCP. Un despachador ASGI mínimo envía `/mcp` y `/.well-known/oauth-protected-resource`
  a la app del SDK y el resto a Django. El mismo despachador maneja el ciclo de vida (sesiones MCP, consumidores,
  Eureka, reconciliación) y el apagado ordenado.
- Sin base de datos: la configuración vive en `ai-config-service`. Django corre sin `DATABASES` ni migraciones.
- Sin Django REST Framework: las únicas vistas HTTP propias son health, readiness y métricas.
- **Abstracción de proveedores:** `AIProvider` (clase abstracta) con `initialize`, `validate_configuration`,
  `list_models`, `generate_response`, `stream_response`, `health_check` y `close`. Las capacidades se declaran en
  `ProviderCapabilities`; lo no soportado lanza `UnsupportedCapabilityError` en vez de simular. Todos los adaptadores
  traducen sus errores a la misma jerarquía (`ProviderError`), y una suite de contrato común lo verifica para cada uno.
- **Registro/fábrica** configurable: `MCP_PROVIDER_ADAPTERS` mapea tipo → clase (`modulo:Clase`). Agregar un
  proveedor es agregar una clase y una línea de configuración; `AIService` no cambia.
- **Credenciales:** se piden a `ai-config-service` con un token de servicio y se guardan solo en memoria, dentro del
  adaptador, con vencimiento (`MCP_CREDENTIAL_TTL_SECONDS`). Al vencer, se cierran y se vuelven a pedir cuando se
  necesitan. Nunca se escriben en disco ni en logs (filtro de redacción en el logging).

### 3. Mensajería

| Routing key | Exchange | Publica → consume | Schema |
|---|---|---|---|
| `ai.provider-config.{created,updated,disabled,deleted,credential-rotated}` | `aplicafacil.events` | ai-config → mcp-gateway | `ai-provider-config-changed-event` |
| `ai.provider.{initialization-started,initialization-succeeded,initialization-failed,test-completed}` | `aplicafacil.events` | mcp-gateway → ai-config | `ai-provider-status-event` |
| `ai.provider.test` | `aplicafacil.commands` | ai-config → mcp-gateway | `ai-provider-test-command` |
| `ai.request.submit` | `aplicafacil.commands` | ai-config → mcp-gateway | `ai-request-command` |
| `ai.request.{completed,failed}` | `aplicafacil.events` | mcp-gateway → ai-config | `ai-request-result-event` |

- **Ningún mensaje lleva una API key**; los mensajes persistentes de RabbitMQ van a disco (misma regla que ADR 0004).
- Cada instancia del mcp-gateway tiene su propia cola **exclusiva** de cambios de configuración (todas deben
  enterarse); los comandos van a una cola **durable compartida** (`mcp-gateway.commands`) con DLQ, para repartir el
  trabajo entre instancias.
- Idempotencia: el mcp-gateway ignora versiones iguales o anteriores a la aplicada y recuerda los `requestId` ya
  procesados; ai-config ignora estados de versiones viejas y resultados de solicitudes ya cerradas.
- **Reconciliación:** al arrancar y cada `MCP_RECONCILE_INTERVAL_SECONDS`, el mcp-gateway compara sus versiones con
  `GET /internal/provider-configs` y corrige lo que se haya perdido mientras estaba apagado.

### 4. Qué es síncrono y qué no

| Interacción | Modo | Por qué |
|---|---|---|
| Tools MCP (`generate_ai_response`, etc.) | Síncrono (petición/respuesta MCP) | El cliente MCP espera la respuesta en la misma llamada; el tiempo lo acota `MCP_GENERATION_TIMEOUT_SECONDS` |
| Alta/edición/rotación desde el front | HTTP síncrono al backend + evento | El backend responde de inmediato (`PENDING`); la inicialización es asíncrona |
| "Probar conexión" y "Probar un prompt" desde el front | Comando + evento con `correlationId` | El backend no llama al proveedor; el front consulta el estado (polling) hasta que llega el resultado o vence (`aplicafacil.ai.request-timeout`) |

### 5. Seguridad del endpoint MCP

`/mcp` exige un JWT de **usuario** de identity-service (`roles` contiene `USER`, `aud = aplicafacil-api`). El
usuario de cada tool sale del `sub` del token, nunca de un argumento, y todas las operaciones se limitan a sus
configuraciones. Se publica la metadata RFC 9728. No hay tools genéricas (ni HTTP arbitrario ni acceso libre al
backend).

### 6. Eureka

Cliente REST propio y pequeño (`infrastructure/eureka.py`): registro, heartbeat cada 30 s, re-registro si Eureka
responde 404 y baja en el apagado. Se descartó `py-eureka-client` para no acoplar el servicio a una librería con
mantenimiento irregular; la API REST de Eureka es estable y basta con cuatro llamadas. Se anuncia
`EUREKA_INSTANCE_HOSTNAME`/`EUREKA_INSTANCE_IP` (configurables) para no publicar nombres que solo resuelve el contenedor.
En local está apagado, como en los servicios Java.

## Consecuencias

- (+) Las credenciales tienen un único dueño; ni el front, ni RabbitMQ, ni los logs las ven.
- (+) Agregar un proveedor no toca la orquestación ni las tools MCP.
- (+) Si el mcp-gateway estuvo apagado, la reconciliación lo pone al día.
- (−) Otra JVM y otro proceso (Python) en la máquina del usuario.
- (−) La API key viaja en claro por loopback entre ai-config-service y el mcp-gateway (como el storageState en ADR 0004).
  En la fase desplegada esto exige TLS o mTLS entre servicios.
- (−) Estado eventual: tras guardar, el front ve `PENDING` unos instantes.

## Limitaciones conocidas

- Sin token exchange: un cliente MCP necesita un token de usuario de la plataforma. Para que `automation-service`
  use las tools en nombre de un usuario hará falta delegación (token exchange o un token de servicio con `act`).
- `stream_ai_response` no se expone como tool: MCP devuelve el resultado de una tool en una sola respuesta. Los
  adaptadores sí implementan `stream_response`, y `generate_ai_response` envía notificaciones de progreso al empezar
  y al terminar.
- La idempotencia de comandos es por instancia (más el filtro del backend por solicitud cerrada).
- Rotar `SESSION_ENCRYPTION_KEY` sigue pendiente (ADR 0003); afecta también a las API keys guardadas.
