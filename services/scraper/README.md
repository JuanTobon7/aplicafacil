# scraper (Node.js + NestJS + Playwright)

Implementación del navegador detrás de `linkedin-service` ([ADR 0004](../../docs/adr/0004-scraper-como-implementacion-del-navegador.md)).
**No tiene base de datos ni lógica de IA, y nunca guarda la sesión:** recibe el storageState en cada petición,
crea un contexto de Playwright NO persistente y devuelve el estado renovado. Quien lo cifra y lo guarda es
linkedin-service.

- Escucha solo en `127.0.0.1:8090`.
- Todo endpoint, salvo `/health`, exige un token de servicio de identity-service (`roles=[SERVICE]`,
  `sub=linkedin-service`).
- Contrato: [`contracts/openapi/scraper-browser.yaml`](../../contracts/openapi/scraper-browser.yaml).

## Arquitectura (hexagonal, misma convención que los servicios Spring)

Paquetes `domain`, `application` (`exception`, `port/in`, `port/out`, `services`), `infrastructure` y `api`
([ADR 0001](../../docs/adr/0001-arquitectura-microservicios.md), punto 4). Las dependencias apuntan hacia adentro y
`test/architecture.test.ts` lo verifica como el ArchitectureTest de Java: `application` no importa Nest, Playwright,
`infrastructure` ni `api`; `api` entra solo por `port/in`; los portales no conocen el motor del navegador.

```
src/
├── main.ts, app.module.ts       # arranque (≈ XxxApplication.java)
├── domain/                      # ProfileSnapshot, StorageState, ConnectStep, BrowserStatus
├── application/
│   ├── exception/               # ScraperError (códigos estables) y LoginErrors (mensajes)
│   ├── port/in/                 # PortalLoginService, PortalSessionService
│   ├── port/out/                # BrowserManager, BrowserSession, BrowserPage…, JobPortal + sus lectores, PortalRegistry
│   └── services/                # PortalLoginServiceImpl, PortalSessionServiceImpl, LoginAttemptRegistry, LoginWatcher…
├── infrastructure/
│   ├── browser/                 # PlaywrightBrowserManager (Singleton), sesión/página Playwright, instalador
│   ├── portal/linkedin/         # selectores, detector de sesión, identidad (/voyager/api/me), perfil
│   ├── security/                # ServiceTokenGuard (JWT de identity-service, JWKS), @Public
│   ├── config/                  # ScraperProperties y módulos Nest: único lugar que elige las implementaciones
│   └── time/
└── api/                         # controladores, ScraperExceptionHandler, NDJSON, DTOs de petición
```

- **Gestor del navegador (Singleton):** `PlaywrightBrowserManager.getInstance()` es el único dueño de Chromium en
  el proceso. Las sesiones headless comparten un Chromium (un contexto aislado por petición); el login visible
  tiene su propio proceso. Al apagar cierra todo.
- **Liskov:** cada portal implementa `JobPortal` y debe pasar la suite
  `test/infrastructure/portal/job-portal.contract.ts`.
- **Inversión de dependencias:** todo se inyecta por tokens de puertos (`BROWSER_SESSION_FACTORY`,
  `PORTAL_REGISTRY`, `PORTAL_LOGIN_SERVICE`…).

### Rutas

- `/portals/{portal}/sessions/{login|validate|read-profile}` y `DELETE …/login/{attemptId}`.
- `/sessions/*` (las del contrato que ya usa linkedin-service) equivalen a `/portals/linkedin/sessions/*`.

### Agregar un portal (p. ej. Computrabajo)

1. `infrastructure/portal/computrabajo/`: selectores + `PortalSessionDetector`, `PortalIdentityReader`,
   `PortalProfileReader` y `createComputrabajoPortal(): JobPortal`.
2. Agregarlo a la lista `JOB_PORTALS` en `infrastructure/config/portals.module.ts`.
3. Test: `describeJobPortalContract("Computrabajo", createComputrabajoPortal)`.

Los servicios de aplicación, el gestor del navegador y los controladores no cambian.

```bash
npm install
npm run build && npm start   # dev-up.ps1 lo hace solo
npm test                     # vitest
```

Pendiente (etapa 5): búsqueda de vacantes y Easy Apply, consumiendo comandos sin secretos por RabbitMQ.
