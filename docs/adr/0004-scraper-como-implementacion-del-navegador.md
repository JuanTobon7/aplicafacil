# ADR 0004 — El scraper (NestJS + Playwright) implementa el navegador; linkedin-service es el proxy

- **Estado:** aceptada
- **Fecha:** 2026-10-15
- **Decisores:** Juan Tobón
- **Modifica:** [ADR 0003](0003-identidad-linkedin-y-sesion-local.md) (dónde corre Playwright)

## Contexto

La ADR 0003 ponía Playwright for Java dentro de `linkedin-service`. Pero en la arquitectura del proyecto
(ADR 0001) el navegador pertenece al **scraper en NestJS** y el MCP al servicio en Python. Los servicios Java
deben abstraerse del navegador.

Las reglas de la sesión siguen intactas: nunca se pide ni se ve la contraseña, el storageState solo persiste
cifrado en PostgreSQL, no se escribe en disco y no se usa `launchPersistentContext`.

## Decisión

**Patrón proxy/puerto:** `linkedin-service` define el puerto `BrowserAutomation` y lo implementa con un adaptador
HTTP (`ScraperBrowserAutomation`). La implementación real está en `services/scraper`.

| | linkedin-service (Java, proxy) | scraper (NestJS, implementación) |
|---|---|---|
| Base de datos | Sí: `linkedin_connection`, `linkedin_session` | No |
| Cifrado | `SessionCryptoService` (AES-256-GCM) | Nunca ve la llave |
| Bloqueo por usuario | Arrendamiento en `linkedin_session` | Un solo inicio de sesión visible a la vez |
| Navegador | No | Playwright para Node: `newContext()` vacío o con `{storageState}` en memoria |
| Lectura de LinkedIn | No | `LinkedInSelectors`, `identity-reader`, `LinkedInProfileReader` |
| Estado / SSE al front | Sí | Emite pasos en NDJSON |

**Transporte:**

- Las operaciones que llevan la sesión (login, validar, leer perfil y, más adelante, postular) van por **HTTP en
  127.0.0.1**, con un token de servicio que el scraper valida contra el JWKS de identity-service y que debe
  incluir `roles=[SERVICE]` y `sub=linkedin-service`.
- **RabbitMQ solo para eventos sin secretos.** Los mensajes persistentes de RabbitMQ se escriben en disco, y la
  sesión no puede quedar ahí.

El contrato está en [`contracts/openapi/scraper-browser.yaml`](../../contracts/openapi/scraper-browser.yaml). El
login responde en streaming NDJSON: eventos `step` y al final un `result` o un `error` con un código estable.

Librería: **Playwright para Node** en lugar de Puppeteer, porque trae `storageState` nativo.

## Consecuencias

- (+) Java no depende del navegador. En la fase desplegada, `BrowserExtensionSessionProvider` implementa el
  mismo `LinkedInSessionProvider` sin cambiar a los consumidores.
- (+) La lógica que depende del DOM de LinkedIn vive en un solo servicio, en el lenguaje del resto del scraping.
- (−) Un proceso más en el equipo del usuario (Node), que `dev-up.ps1` arranca junto con los demás.
- (−) El storageState viaja en claro por loopback entre dos procesos locales. Lo mitigan el bind a 127.0.0.1 y el
  token de servicio; nunca sale del equipo.
- El código de Playwright for Java quedó en `legacy/services/backend/linkedin-service-playwright-java`.
