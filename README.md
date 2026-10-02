# aplicafacil

Proyecto de automatización de búsqueda y postulación a empleos. Arquitectura de microservicios
(Java + Node + Python). Decisiones en [docs/adr](docs/adr).

```
aplicafacil/
├── apps/
│   ├── web/                     # React + Vite
│   └── extension/               # Extensión de Chrome
├── services/
│   ├── backend/                 # Java 25 · Spring Boot 4.1 · Spring Cloud 2025.1 (Maven multi-módulo)
│   │   ├── config-repo/         # YAML servidos por el config-server
│   │   ├── libs/platform-common/
│   │   ├── platform/
│   │   │   ├── config-server/     :8888
│   │   │   ├── discovery-server/  :8761
│   │   │   └── api-gateway/       :8080
│   │   ├── auth-service/          :9000  OAuth 2.1 / OIDC (login, registro, tokens)
│   │   ├── people-service/        :8081
│   │   ├── profile-service/       :8084
│   │   ├── jobs-service/          :8082
│   │   └── automation-service/    :8083
│   ├── scraper/                 # Node + NestJS + Puppeteer
│   └── mcp-gateway/             # Python, servidor MCP
├── contracts/                   # JSON Schema, AsyncAPI, OpenAPI
├── infra/                       # docker-compose, init de Postgres
└── docs/adr/
```

## Levantar en local

1. Infraestructura (Postgres + pgvector, RabbitMQ, Redis):
   ```bash
   cp infra/.env.example infra/.env
   docker compose -f infra/docker-compose.yml up -d
   ```
   Puertos en el host: Postgres **5434**, RabbitMQ 5672/15672, Redis **6380**
   (5432, 5433 y 6379 ya están ocupados en esta máquina).
2. Servicios Java, en este orden (desde `services/backend`, o desde el IDE):
   ```bash
   ./mvnw install -DskipTests          # una vez: instala platform-common
   ./mvnw -pl platform/config-server spring-boot:run
   ./mvnw -pl platform/discovery-server spring-boot:run
   ./mvnw -pl auth-service spring-boot:run
   ./mvnw -pl platform/api-gateway spring-boot:run
   ./mvnw -pl people-service spring-boot:run      # igual para profile-, jobs- y automation-service
   ```
   auth-service necesita las variables de `infra/.env` (secretos de los clientes y admin inicial).
   También se pueden levantar todos en contenedores: `docker compose -f infra/docker-compose.yml --profile apps up -d --build`.

| UI | URL |
|---|---|
| Login / registro | http://localhost:9000/login |
| OpenID discovery | http://localhost:9000/.well-known/openid-configuration |
| Eureka | http://localhost:8761 |
| RabbitMQ | http://localhost:15672 |
| Swagger de cada servicio | http://localhost:808X/swagger-ui.html |

## Autenticación (OAuth 2.1 / OIDC)

`auth-service` es el servidor de autorización ([ADR 0002](docs/adr/0002-auth-service-propio.md)).

| Quién | Cómo obtiene el token |
|---|---|
| Front web / extensión | Authorization Code + PKCE con el cliente público `aplicafacil-web` → `http://localhost:9000/oauth2/authorize` |
| automation-service | `client_credentials` con `automation-service` |
| mcp-gateway | token exchange (RFC 8693) con `mcp-gateway` |

Todos los servicios validan el JWT (firma, `iss = http://localhost:9000` y `aud = aplicafacil-api`).
Cuenta propia: `GET /api/auth/me` y `POST /api/auth/me/password`. Administración (rol ADMIN):
`GET /api/auth/users` y `PATCH /api/auth/users/{id}/enabled`.
