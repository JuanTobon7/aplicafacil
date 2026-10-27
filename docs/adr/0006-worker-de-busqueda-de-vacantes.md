# ADR 0006 — Worker de búsqueda de vacantes (LinkedIn, Computrabajo)

- **Estado:** propuesta
- **Fecha:** 2026-10-16
- **Decisores:** Juan Tobón
- **Relacionada con:** [ADR 0003](0003-identidad-linkedin-y-sesion-local.md) (sesión cifrada, una automatización a
  la vez por usuario), [ADR 0004](0004-scraper-como-implementacion-del-navegador.md) (el navegador vive en el scraper)

## Contexto

Hace falta un proceso que, pedido desde `jobs-service`, abra el navegador en un portal de empleo, busque, recorra
las cards de las ofertas y las guarde en la BD. Hoy hay dos portales (LinkedIn, que necesita la sesión del usuario,
y Computrabajo, cuyo listado es público) y vendrán más.

Restricciones que vienen de ADR 0003/0004: solo `linkedin-service` toca la sesión de LinkedIn (cifrada, con lease por
usuario), solo el scraper maneja el navegador, y la sesión nunca pasa por RabbitMQ.

## Decisión

```
 Front ──POST /api/jobs/searches──▶ jobs-service ── job_search (QUEUED) + outbox ──┐
                                                                           │ RabbitMQ: jobs.search.requested
                                                                           │ JobSearchListener → JobSearchWorker
                                                                           ▼
                                          JobSearchFacade (Facade) ── JobPortalGateways (registro de estrategias)
                                             │                              ├ LinkedInJobPortalGateway ──▶ linkedin-service
                                             │                              │      POST /internal/sessions/{u}/job-search
                                             │                              │      lease + descifra + LinkedInAction.SearchJobs ─┐
                                             │                              └ ComputrabajoJobPortalGateway ─────────────────────┤
                                             ▼                                                                                  ▼
                                   JobPostingStore (upsert)                                scraper: POST /portals/{p}/jobs/search
                                   job_posting · job_search_result                         JobSearchFacade (Facade) + JobSearchPortal (Strategy)
```

### Patrones

| Patrón | Dónde | Para qué |
|---|---|---|
| **Facade** | `jobs-service` `JobSearchFacade` | El worker hace una sola llamada; la fachada elige el portal, pide la búsqueda, limpia y deduplica las cards y las guarda |
| **Facade** | `scraper` `JobSearchFacade` | Esconde los pasos del navegador: abrir contexto headless, validar la sesión, navegar, esperar el listado, desplazar para cargar cards perezosas, recorrerlas, paginar, devolver el storageState renovado y cerrar siempre |
| **Strategy** | `JobPortalGateway` (Java) y `JobSearchPortal` (scraper) | Lo que cambia por portal: a quién pedir (Java) y URLs, selectores y mapeo de una card (scraper) |
| **Registry** | `JobPortalGateways`, `InMemoryJobSearchPortalRegistry` | Resolver la estrategia por plataforma sin `if/switch`; agregar un portal no toca las fachadas |
| **Template Method** | `HttpJobPortalGateway` | Cliente HTTP, token de servicio, timeouts y traducción de errores comunes; cada portal define ruta y cuerpo |
| **Iterator (paginado)** | `JobSearchFacade.collect` (scraper) | Recorre página por página hasta `maxResults`, una página incompleta o una que solo repite |
| **Command** | `LinkedInAction.SearchJobs` | La acción viaja como dato; en la fase desplegada la misma acción irá a la extensión del navegador |

`JobSearchPortal` está separado de `JobPortal` (login, identidad, perfil) por segregación de interfaces: Computrabajo
se busca sin iniciar sesión, así que no tiene por qué implementar un detector de sesión ni un lector de perfil.

### Cola y worker

- **RabbitMQ reparte, PostgreSQL decide.** Como toda la comunicación asíncrona del proyecto, la cola es RabbitMQ: el
  comando `jobs.search.requested` (contracts/schemas/job-search-requested-command.schema.json) lleva solo ids; los
  criterios están en `jobs.job_search` y la sesión de LinkedIn nunca viaja por la cola.
- **Outbox:** pedir la búsqueda guarda la fila y el comando en la misma transacción; `OutboxPublisher` lo publica en
  `aplicafacil.commands`. Si RabbitMQ está caído, el comando espera en la tabla: no se pierde ninguna búsqueda.
- **Consumo:** `JobSearchListener` sobre `jobs-service.job-searches` (durable, compartida entre instancias), prefetch 1.
  El mensaje se confirma al terminar el intento; si la instancia muere antes, RabbitMQ lo reentrega a otra. Eso
  reemplaza el candado `locked_until` y el sondeo de la tabla (V3 los elimina).
- **Estado por id, sin subconsultas:** `JdbcJobSearchStates.start` es un `UPDATE … WHERE id = :id` que acepta un intento
  nuevo (QUEUED con un intento anterior) o el mismo intento reentregado (RUNNING con ese intento). Un mensaje repetido
  o atrasado (búsqueda terminada, borrada por `user.deleted` o ya en otro intento) se confirma sin hacer nada.
- **Reintentos con espera:** cada intento n tiene su cola `jobs-service.job-searches.wait.{n}` con
  `x-message-ttl` = 30 s · 2^(n-2) (tope 10 min); al vencer, el mensaje vuelve por dead-letter a la cola de trabajo.
  Una cola por intento evita que un mensaje con espera corta quede detrás de uno con espera larga.
- **Fallos de infraestructura** (la BD no responde al recibir el mensaje): el mismo intento pasa por
  `jobs-service.job-searches.wait.redeliver` (30 s) y vuelve. Solo un mensaje ilegible va a `aplicafacil.dlx` →
  `jobs-service.job-searches.dlq`.

  *Antes (hasta 2026-10-23):* la cola era la propia tabla, sondeada cada 5 s con
  `UPDATE … FROM (SELECT … FOR UPDATE SKIP LOCKED)`. Se reemplazó por RabbitMQ para quitar esa consulta anidada y
  seguir la regla del proyecto: toda cola y toda comunicación asíncrona va por RabbitMQ.
- Concurrencia por instancia: 2 (cada búsqueda es un Chromium headless en el scraper). LinkedIn además está limitado a
  una automatización por usuario por el lease de `linkedin-service` (`SESSION_BUSY`, que se reintenta).
- Reintentos con espera exponencial (ver arriba) hasta 3 intentos para errores
  transitorios (`PORTAL_UNAVAILABLE`, `SESSION_BUSY`, `SEARCH_FAILED`, 5xx). Definitivos sin reintento:
  `NOT_CONNECTED`, `SESSION_EXPIRED`, `SESSION_REQUIRED`, `INVALID_REQUEST`.
- Cada transición exige el estado esperado (`WHERE status = 'RUNNING'`): un worker atrasado no pisa un estado final.

### Datos

- `job_posting`: una fila por `(platform, external_id)`; los datos se actualizan cuando la oferta vuelve a aparecer
  (`last_seen_at`). Son datos públicos de los portales: se conservan cuando se borra un usuario.
- `job_search_result`: qué ofertas encontró cada búsqueda, en qué orden y si eran nuevas (`xmax = 0` del upsert).
- `job_search`: la búsqueda del usuario; se borra con `user.deleted` (sus resultados en cascada).

### Ser buen ciudadano con los portales

Pausa al azar de 1,5–3,5 s entre páginas, tope de 10 páginas y 200 ofertas por búsqueda, como mucho 5 búsquedas
pendientes por usuario. Solo se leen las cards del listado (no se abre cada oferta). La automatización de LinkedIn
infringe sus términos; el riesgo ya se comunica en el consentimiento (ADR 0003).

## Consecuencias

- (+) Un portal nuevo = una estrategia en el scraper (`JobSearchPortal` + selectores) y un `JobPortalGateway` en Java.
  Ni el worker ni las fachadas cambian. Ambos lados tienen suite de contrato.
- (+) La sesión de LinkedIn sigue con un solo dueño; jobs-service nunca ve el storageState.
- (−) Los selectores dependen del DOM de cada portal y van a cambiar. Están aislados en `*.selectors.ts`; un listado
  que no aparece falla con `SEARCH_FAILED` en vez de devolver cero ofertas en silencio.
- Los selectores de Computrabajo se validaron contra co.computrabajo.com (2026-10-16): 20 ofertas por página,
  paginación `?p=N`; las pruebas usan una card real. Los del listado de LinkedIn siguen sin validar en vivo (hace falta
  una sesión conectada).
- Computrabajo responde **403** a un user-agent con `HeadlessChrome`: los contextos headless del scraper se anuncian
  como Chrome de escritorio con la versión real del Chromium (`desktopUserAgent`).
- Un fallo de navegación (DNS, sin red, timeout) sale como `PORTAL_UNREACHABLE`, reintentable.
- (−) El scraper acepta ahora a `jobs-service` como cliente; el portal con sesión sigue exigiendo storageState, que
  jobs-service no tiene.
- Front: página `/vacantes` para pedir búsquedas, seguir su estado (polling mientras hay búsquedas en curso) y ver las
  ofertas encontradas.
- Pendiente: búsquedas programadas (recurrentes) y leer la descripción completa de cada oferta.
