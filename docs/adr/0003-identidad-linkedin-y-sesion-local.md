# ADR 0003 — LinkedIn como identidad y sesión local cifrada

- **Estado:** propuesta
- **Fecha:** 2026-10-15
- **Decisores:** Juan Tobón
- **Reemplaza:** la parte de identidad de [ADR 0001](0001-arquitectura-microservicios.md) y por completo [ADR 0002](0002-auth-service-propio.md)

## Contexto

La plataforma automatiza postulaciones a empleos. Tiene dos fases:

1. **Local (actual):** cada usuario la ejecuta en su propia máquina. El backend debe abrir un navegador
   **visible** para que la persona inicie sesión en LinkedIn y luego reutilizar esa sesión para postular.
2. **Desplegada (futura):** multiusuario en un servidor. Allí no hay navegador visible, la identidad llega por
   OAuth 2.0 / OIDC de LinkedIn y las acciones se ejecutan en una extensión del navegador del usuario.

ADR 0002 definió un `auth-service` con registro y login por usuario/contraseña. Ahora el requisito es que
**LinkedIn sea el registro y el login**: no existen contraseñas propias. Cada perfil de LinkedIn conectado es
un usuario de la plataforma, y varios pueden convivir en la misma instalación.

Restricciones absolutas:

- La app nunca pide, recibe, registra en logs ni almacena la contraseña de LinkedIn.
- La sesión de LinkedIn (`storageState` de Playwright) solo persiste en PostgreSQL, cifrada con AES-256-GCM.
  No va a carpetas del sistema operativo, al llavero ni a archivos temporales. No se usa `launchPersistentContext`.
- Ninguna página web externa puede llamar la API local.

## Decisión

Se mantiene la arquitectura de microservicios (ADR 0001), pero se redistribuyen las responsabilidades:

| Servicio | Estado | Responsabilidad |
|---|---|---|
| `identity-service` | **nuevo** (reemplaza a `auth-service`) | Dueño de `app_user`, `refresh_token`, `oauth_token`, `signing_key` y del outbox. Contiene `UserProvisioningService`. Emite JWT RS256 propios, publica JWKS y tiene el cliente OAuth2 de LinkedIn (desactivado por defecto) |
| `linkedin-service` | **nuevo** | Dueño de `linkedin_connection` y `linkedin_session`: cifrado, bloqueo por usuario, estado y SSE. Proxy del navegador: delega en el scraper ([ADR 0004](0004-scraper-como-implementacion-del-navegador.md)) |
| `scraper` (NestJS) | **nuevo** | Implementación del navegador con Playwright para Node; sin BD. **Corre siempre en el host** (navegador visible) |
| `profile-service` | se reutiliza | `user_profile` en JSONB (experiencia, educación, habilidades, idiomas, resumen) y el mapa de campos editados a mano. Las tablas relacionales anteriores se eliminan |
| `jobs-service` | se reutiliza | `job_application` (registro de cada intento) |
| `automation-service` | se reutiliza | `ApplicationProvider` (Easy Apply, Greenhouse, Lever, Workday), límite diario y pausas. Usa la sesión **solo** a través de `linkedin-service` |
| `api-gateway` | se reutiliza | Sirve el build de React, inyecta el token local y lo exige en `/api/**`. Rutas a `127.0.0.1` en local |
| `auth-service`, `people-service` | **a `legacy/`** | Fuera del build y del compose. Se conservan como referencia |
| `config-server`, `discovery-server` | solo fase desplegada | En local no corren: cada servicio carga `config-repo` desde archivos (perfil `local`) y Eureka se desactiva |

### Ejecución local

- `docker compose up -d` levanta PostgreSQL 18 con pgvector (publicado en `127.0.0.1`), RabbitMQ y Redis;
  pgAdmin solo con `--profile tools`.
- Los servicios Java corren en el host con el perfil `local` y escuchan en `127.0.0.1`. `linkedin-service`
  tiene que correr en el host porque abre el navegador visible.
- El gateway sirve el front en `http://127.0.0.1:8080`, así que front y API comparten origen y no hace falta CORS.
  En desarrollo, Vite (5173) hace proxy de `/api` hacia el gateway.

### Token local por arranque

El gateway genera 32 bytes aleatorios al arrancar y los inyecta en el `index.html` que sirve
(`<meta name="af-local-token">`). El front los envía en el header `X-Local-Token`, y el gateway rechaza con 403
toda petición a `/api/**` que no lo traiga, además de validar `Origin` cuando el navegador lo envía.
Una página externa no puede leer ese `index.html` (lo impide la política de mismo origen), así que no conoce el token.
En desarrollo, el proxy de Vite obtiene el token de `GET /__local-token`, que solo responde a peticiones desde loopback.

### Identidad y sesión de la plataforma

- `app_user` tiene `linkedin_member_urn` (fase local) y `linkedin_oidc_sub` (fase desplegada). Ambas columnas son
  únicas y nulables, y al menos una debe tener valor. El correo verificado sirve como clave secundaria para vincular
  una cuenta creada en un modo con la del otro.
- `UserProvisioningService.provision(LinkedInIdentity)` es el único punto que decide si **crea**, **actualiza** o
  **vincula** un usuario, sin importar de qué proveedor venga la identidad. Orden de búsqueda: identificador del
  proveedor → el otro identificador → correo verificado → crear.
- JWT de acceso RS256 de 15 minutos (`sub` = id del usuario, `aud = aplicafacil-api`).
- Refresh token opaco, rotativo, guardado como hash SHA-256, en cookie `af_rt` con `HttpOnly`, `Secure`,
  `SameSite=Strict` y `Path=/api/auth`. Los navegadores tratan `localhost` y `127.0.0.1` como contexto seguro,
  así que la cookie `Secure` funciona sin TLS en local.
- La llave de firma RS256 se genera la primera vez y se guarda en `identity.signing_key`, cifrada con
  `SessionCryptoService`. Las sesiones sobreviven a los reinicios.
- Llamadas entre servicios: tokens de servicio (`roles=[SERVICE]`) que firma `identity-service` con la misma llave.
  Las rutas `/internal/**` solo aceptan ese rol.

### Conectar = iniciar sesión

Al empezar la conexión todavía no hay usuario, así que el estado `CONNECTING` pertenece a un **intento**
(`attemptId`) que vive en memoria en `linkedin-service` y no a un usuario. `linkedin_connection` guarda el estado
persistente (`CONNECTED`, `EXPIRED`, `DISCONNECTED`) de cada usuario.

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuario
    participant W as React
    participant G as api-gateway
    participant L as linkedin-service
    participant B as Chromium (headful)
    participant I as identity-service
    participant P as profile-service
    participant DB as PostgreSQL

    U->>W: Abrir LinkedIn (tras el consentimiento)
    W->>G: POST /api/linkedin/connect (X-Local-Token)
    G->>L: POST /linkedin/connect
    L-->>W: 202 {attemptId}
    W->>G: GET /api/linkedin/connect/{attemptId}/events (SSE)
    L->>B: browser.newContext() vacío → linkedin.com/login
    Note over L,W: paso WAITING_FOR_LOGIN
    U->>B: inicia sesión (2FA o captcha; timeout de 5 min)
    L->>B: detecta la cookie li_at o la URL /feed
    Note over L,W: paso READING_PROFILE
    L->>B: LinkedInProfileReader (memberUrn + perfil)
    Note over L,W: paso CREATING_ACCOUNT
    L->>I: POST /internal/users/provision (LinkedInIdentity)
    I->>DB: crear o actualizar app_user
    I-->>L: {userId, created, loginCode (un solo uso, 60 s)}
    L->>B: context.storageState() (String en memoria)
    L->>DB: linkedin_session ← AES-GCM(storageState), iv, key_version
    L->>P: PUT /internal/profiles/{userId}/sync (respeta los campos editados)
    L->>B: cerrar el navegador
    L->>DB: linkedin_connection = CONNECTED
    L-->>W: SSE {status: CONNECTED, loginCode}
    W->>G: POST /api/auth/session {attemptId, loginCode}
    G->>I: canjear el código
    I-->>W: {accessToken} + Set-Cookie af_rt
```

"Continuar como…": `GET /api/auth/accounts` lista los usuarios locales. Elegir uno dispara
`POST /api/auth/accounts/{id}/session`. `identity-service` le pide a `linkedin-service` que valide la sesión
guardada (en modo headless). Si es válida, emite los tokens; si expiró, la marca `EXPIRED` y el front ofrece reconectar.
La instalación local confía en el usuario del sistema operativo: quien tiene acceso a la máquina tiene acceso a las
sesiones guardadas en ella. Así queda documentado; se mitiga con el token local y con el bind a `127.0.0.1`.

### Borrado de cuenta

`DELETE /api/auth/me`:

1. `identity-service` llama de forma síncrona a `linkedin-service` para borrar la sesión. Si falla, se aborta,
   para que nunca quede una sesión huérfana.
2. En una sola transacción borra `app_user` (los tokens se borran en cascada) e inserta `user.deleted` en el outbox.
3. Un publicador envía el outbox a RabbitMQ. `profile-service`, `jobs-service`, `automation-service` y
   `linkedin-service` borran lo suyo de forma idempotente.

### Abstracciones (para no reescribir al desplegar)

- `LinkedInIdentityProvider` → `SessionIdentityProvider` (local) | `OidcIdentityProvider` (desplegada).
- `LinkedInSessionProvider` → `LocalPlaywrightSessionProvider` (local, completo) | `BrowserExtensionSessionProvider` (stub).
- `LinkedInSessionRepository`: único punto de lectura y escritura de `linkedin_session`.
- `SessionCryptoService` (librería `libs/platform-crypto`): AES-256-GCM, IV de 12 bytes por escritura y `key_version`.
  Si `SESSION_ENCRYPTION_KEY` falta o es inválida, el servicio no arranca. Lo usan `linkedin-service` (sesión) e
  `identity-service` (tokens OAuth y llave de firma).

## Opciones consideradas

### A. Monolito modular local
| Dimensión | Evaluación |
|---|---|
| Complejidad | Baja |
| Costo en la máquina del usuario | 1 JVM |
| Escalabilidad | Hay que extraer servicios al desplegar |
| Familiaridad | Alta |

**Pros:** arranque simple, transacciones locales para el borrado. **Contras:** contradice ADR 0001 y obliga a una
migración al desplegar.

### B. Microservicios con identity-service y linkedin-service (elegida)
| Dimensión | Evaluación |
|---|---|
| Complejidad | Alta |
| Costo en la máquina del usuario | 5 a 6 JVMs más 3 contenedores |
| Escalabilidad | Ya lista para la fase desplegada |
| Familiaridad | Alta (continúa ADR 0001) |

**Pros:** la sesión de LinkedIn tiene un solo dueño aislado, la fase desplegada no exige reescribir y se respetan
los esquemas por servicio. **Contras:** consumo de memoria en la máquina del usuario, consistencia eventual en el
borrado y llamadas internas que requieren tokens de servicio.

### C. Reconvertir auth-service
Se descartó porque Spring Authorization Server y los flujos de contraseña quedan sin uso y confunden el modelo.

## Consecuencias

- (+) Un solo dueño para la sesión: ningún otro servicio ve cookies de LinkedIn.
- (+) La lógica de identidad es la misma para Playwright y para OIDC.
- (−) Arrancar en local implica levantar varias JVMs. Los scripts `dev-up.ps1` y `dev-up.cmd` las orquestan.
  Revisar el empaquetado (por ejemplo, un lanzador único) antes de distribuir a usuarios no técnicos.
- (−) Cambia el esquema de `profile-service`, así que los datos de desarrollo anteriores se pierden. También cambian
  los esquemas de `infra/postgres/init`, lo que exige recrear el volumen (`docker compose down -v`).
- (−) La automatización de LinkedIn infringe sus términos de uso; el riesgo se le comunica al usuario en la pantalla
  de consentimiento.
- Por revisar: el empaquetado para usuarios finales, la rotación de `SESSION_ENCRYPTION_KEY` (re-cifrado por
  `key_version`) y la autenticación entre servicios en la fase desplegada (mTLS o tokens de servicio con expiración corta).

## Plan por etapas

1. [x] Docker, migraciones, entidades e interfaces; mover el código antiguo a `legacy/`.
2. [x] `SessionCryptoService`, captura de la sesión y creación del usuario de punta a punta (incluye la emisión de JWT, el token local y el front de conexión).
3. [ ] Lectura completa del perfil, edición y "Sincronizar de nuevo".
4. [ ] OAuth 2.0 de LinkedIn (`app.linkedin.oauth.enabled`).
5. [ ] Módulo de automatización de postulaciones.
