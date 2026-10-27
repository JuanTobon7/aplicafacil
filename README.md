# aplicafacil

Automatización de búsqueda y postulación a empleos. Microservicios Java + Node + Python. Tu cuenta de
LinkedIn es tu cuenta en la plataforma: no hay contraseñas propias. Decisiones en [docs/adr](docs/adr)
(las vigentes para identidad y sesión: [ADR 0003](docs/adr/0003-identidad-linkedin-y-sesion-local.md) y
[ADR 0004](docs/adr/0004-scraper-como-implementacion-del-navegador.md)).

```
aplicafacil/
├── apps/
│   ├── web/                     # React + Vite (el gateway sirve el build)
│   └── extension/               # Extensión de Chrome (fase desplegada)
├── services/
│   ├── backend/                 # Java 25 · Spring Boot 4.1 (Maven multi-módulo)
│   │   ├── config-repo/         # YAML de cada servicio; *-local.yml = fase local
│   │   ├── libs/
│   │   │   ├── platform-common/   # seguridad (resource server) y errores
│   │   │   ├── platform-crypto/   # SessionCryptoService (AES-256-GCM)
│   │   │   └── linkedin-contract/ # records de identidad y perfil compartidos
│   │   ├── platform/api-gateway/  :8080  front + token local + rutas /api/**
│   │   ├── identity-service/      :9000  usuarios, JWT propios, refresh, cuentas
│   │   ├── linkedin-service/      :8085  sesión cifrada, estado, SSE (proxy del scraper)
│   │   ├── computrabajo-service/  :8087  sesión de candidato de Computrabajo cifrada (ADR 0008)
│   │   ├── profile-service/       :8084
│   │   ├── jobs-service/          :8082  postulaciones y worker de búsqueda de vacantes (ADR 0006)
│   │   ├── automation-service/    :8083
│   │   └── ai-config-service/     :8086  proveedores de IA por usuario, API keys cifradas (ADR 0005)
│   ├── scraper/                 :8090  NestJS + Playwright: el navegador (sin BD)
│   └── mcp-gateway/             :8000  Python · Django + SDK MCP: tools sobre los proveedores de IA (ADR 0005)
├── contracts/                   # JSON Schema, AsyncAPI, OpenAPI
├── infra/                       # docker-compose, init de Postgres
└── legacy/                      # código retirado (auth con contraseña, Playwright en Java…)
```

## Levantar en local

Requisitos: Docker Desktop, JDK 25, Node.js 22+ y algún Python (para instalar `uv`; el script se encarga del resto).

```bash
.\dev-up.cmd
```

El script:

1. Si Docker Desktop está apagado, lo abre y espera a que responda.
2. Crea `infra/.env` desde `.env.example` y genera `SESSION_ENCRYPTION_KEY` y `SERVICE_CLIENT_SECRET` si están vacías.
3. Levanta Postgres 18 + pgvector, RabbitMQ y Redis (`docker compose up -d`), publicados **solo en 127.0.0.1**,
   y crea los schemas que falten sin borrar datos.
4. Compila (la primera vez o con `-Build`): servicios Java con JDK 25, scraper, web y el entorno Python del
   mcp-gateway. Si falta `uv` lo instala con pip (o winget), y `uv` descarga Python 3.13 si no está.
5. Arranca en este equipo el scraper, los servicios Java (perfil `local`, sin config-server ni Eureka) y el
   mcp-gateway. Los logs quedan en `logs\`.
6. Abre http://127.0.0.1:8080 y **se queda en primer plano** mostrando la actividad del worker de búsquedas.
   **Ctrl+C detiene todo**: los servicios y los contenedores (los datos se conservan).

Opciones: `-Build` (recompilar), `-Dev` (además Vite con recarga en caliente en http://127.0.0.1:5173),
`-Test` (no arranca nada: corre todas las pruebas —Java y mcp-gateway con Postgres/RabbitMQ reales, scraper y
typecheck del front— y muestra un resumen; sale con código 1 si alguna falla), `-ShowBrowser` (las búsquedas de
vacantes se hacen en una ventana visible del navegador y la consola muestra cada paso), `-KeepInfra` (Ctrl+C deja
corriendo Postgres, RabbitMQ y Redis), `-Detach` (deja todo en segundo plano, como antes) y
`-Down` (detiene lo que haya quedado en segundo plano, sin borrar datos). pgAdmin:
`docker compose -f infra/docker-compose.yml --profile tools up -d` → http://127.0.0.1:5050.

La primera vez el scraper descarga Chromium; mientras tanto "Abrir LinkedIn" responde que el navegador aún
no está listo.

### Por qué así

- **El scraper y los servicios Java corren en el host, no en Docker:** el inicio de sesión abre un navegador visible.
- **La sesión de LinkedIn** (storageState de Playwright) solo persiste **cifrada** en PostgreSQL. El scraper la
  recibe por HTTP local en cada operación y nunca la escribe en disco.
- **Token local:** el gateway genera uno en cada arranque, lo inyecta en el `index.html` y lo exige en `/api/**`.
  También rechaza un Host o un Origin ajenos (DNS rebinding). Ninguna página externa puede llamar la API local.

## Identidad

| Paso | Qué pasa |
|---|---|
| Conectar / Agregar cuenta | El scraper abre linkedin.com/login, la persona inicia sesión, se lee la identidad y `identity-service` crea, actualiza o vincula el usuario |
| Entrar | El front canjea un código de un solo uso por un JWT (15 min, en memoria) y la cookie `af_rt` (HttpOnly, Secure, SameSite=Strict) |
| "Continuar como…" | Se valida la sesión guardada (headless) antes de emitir tokens |
| Entre servicios | Tokens de servicio (`roles=[SERVICE]`) de `identity-service` (`/internal/token`) |

Todos los servicios validan el JWT con el JWKS de `identity-service` (`http://127.0.0.1:9000/.well-known/jwks.json`).

## Proveedores de IA y MCP

En **Proveedores de IA** (`/ia`) cada persona agrega OpenAI (o una API compatible) u OpenRouter con su API key,
elige modelo y parámetros, prueba la conexión y ve el estado (`PENDING` → `ACTIVE`/`FAILED`). La key se guarda
cifrada en `ai-config-service` y nunca vuelve al navegador ni viaja por RabbitMQ. El `mcp-gateway` expone esas
configuraciones como tools MCP en `http://127.0.0.1:8000/mcp`. Detalle: [ADR 0005](docs/adr/0005-mcp-gateway-y-proveedores-de-ia.md)
y [services/mcp-gateway](services/mcp-gateway/README.md).

## Computrabajo

En **Mi perfil → Computrabajo** se abre una ventana para iniciar sesión como candidato (también con Google); la sesión
queda cifrada en `computrabajo-service`. Al abrir la app se comprueba que siga viva y, si venció, se pide iniciar sesión
de nuevo. Detalle: [ADR 0008](docs/adr/0008-sesion-de-computrabajo.md).

## Búsqueda de vacantes

`POST /api/jobs/searches` (`{"platform": "LINKEDIN" | "COMPUTRABAJO", "keywords", "location", "maxResults",
"postedWithinDays"}`) guarda la búsqueda, la encola en RabbitMQ (`jobs.search.requested`, por outbox) y responde 202. El worker de `jobs-service` la toma, abre el navegador
headless del scraper (LinkedIn con tu sesión, a través de `linkedin-service`; Computrabajo sin sesión), recorre las
cards del listado y guarda las ofertas. Consulta el estado en `GET /api/jobs/searches/{id}` y las ofertas en
`GET /api/jobs/searches/{id}/postings`. Diseño y patrones: [ADR 0006](docs/adr/0006-worker-de-busqueda-de-vacantes.md).

## Calidad

El listón del repo está en [CONSTRAINTS.md](CONSTRAINTS.md): líneas por función, complejidad, acoplamiento entre capas,
secretos y dependencias, cada uno con su número, su motivo y el comando que lo verifica.

```bash
.\quality.cmd            # al terminar una tarea (piso, tamaño, capas y pruebas de lo cambiado)
.\quality.cmd -Fast      # mientras se edita
.\quality.cmd -Full      # antes de integrar (incluye gitleaks y osv-scanner)
```

Las reglas de capas, ciclos, inversión de dependencias y puertos chicos (ArchUnit) no tienen deuda: cualquier violación
falla. Lo único heredado de tamaño/complejidad está en `tools/quality/baseline.json` y no puede crecer. SOLID y GRASP,
con cómo se ven en este repo, están en la sección "Diseño" de [CONSTRAINTS.md](CONSTRAINTS.md).
