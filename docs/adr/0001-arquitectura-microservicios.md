# ADR 0001 — Arquitectura de microservicios políglota

- **Estado:** aceptada
- **Fecha:** 2026-10-01

## Contexto

AplicaFacil era un monolito NestJS (`aplicafacil-server`) con una librería compartida en TypeScript
(`@aplicafacil/core`) y un servidor MCP en TypeScript. Se busca separar responsabilidades por servicio,
usar Java/Spring para el núcleo de negocio y Python para el servidor MCP, y soportar varios usuarios con OAuth 2.0.

> Actualización: Keycloak fue reemplazado por `auth-service` (ver [ADR 0002](0002-auth-service-propio.md)).

## Decisión

| Servicio | Tecnología | Responsabilidad |
|---|---|---|
| `config-server` | Spring Cloud Config (native) | Configuración centralizada (`services/backend/config-repo`) |
| `discovery-server` | Eureka | Registro y descubrimiento de servicios |
| `api-gateway` | Spring Cloud Gateway Server MVC | Entrada única, validación de JWT, CORS, enrutamiento `/api/<servicio>/**` |
| `people-service` | Spring Boot | Personas: datos personales y de contacto |
| `profile-service` | Spring Boot | Perfiles: skills, experiencia, educación, proyectos, CV, embeddings (pgvector) |
| `jobs-service` | Spring Boot | Vacantes, ciclo de vida de postulaciones, matching |
| `automation-service` | Spring Boot | Scheduler/worker, comandos al scraper, agente LLM, cliente MCP |
| `scraper` | Node + NestJS + Puppeteer | Solo navegador: buscar, extraer y postularse |
| `mcp-gateway` | Python (MCP SDK) | Expone capacidades como tools; resource server OAuth |
| `auth-service` | Spring Authorization Server | Authorization Server OAuth 2.1 / OIDC: usuarios, login, registro, tokens ([ADR 0002](0002-auth-service-propio.md)) |

Reglas:

1. **Datos:** cada servicio Java es dueño de su schema en Postgres y tiene su propio usuario de BD. No hay joins entre servicios.
2. **Comunicación:** REST síncrono para consultas (por Eureka, con token de servicio) y RabbitMQ para las tareas largas
   (postularse a una vacante tarda minutos).
3. **Lo compartido son contratos, no código** (`contracts/`). `platform-common` solo contiene infraestructura (seguridad, errores).
4. **Hexagonal por servicio:** paquetes `domain`, `application`, `infrastructure` y `api`.
5. **Seguridad:** todos los servicios validan el JWT (`aud = aplicafacil-api`), no solo el gateway.
   El mcp-gateway nunca reenvía tokens; hace token exchange.
6. **Web MVC + virtual threads** en lugar de WebFlux: JPA es bloqueante y la carga no justifica programación reactiva.

## Consecuencias

- (+) Cada servicio se despliega, escala y falla de forma independiente.
- (+) La IA y la seguridad quedan centralizadas en servicios con un dueño claro.
- (−) Más piezas que operar (config, eureka, gateway, keycloak, rabbit). Se mitiga con `infra/docker-compose.yml`.
- (−) Las consistencias entre servicios son eventuales; la máquina de estados de postulaciones debe ser idempotente.
- (−) Hay que mantener los contratos sincronizados entre tres lenguajes.
