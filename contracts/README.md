# contracts

Lo único que se comparte entre lenguajes (Java, Node, Python). Nunca código, solo la **forma** de los datos.

| Carpeta | Estándar | Qué describe |
|---|---|---|
| `schemas/` | JSON Schema 2020-12 | Forma de cada DTO o mensaje |
| `events/` | AsyncAPI 3 | Qué mensajes viajan por RabbitMQ, en qué cola y quién publica o consume |
| `openapi/` | OpenAPI 3.1 | APIs REST de los servicios Java |

## Flujo

- **REST (code-first):** cada servicio Java publica su OpenAPI en `/v3/api-docs` (springdoc). Se guarda aquí con:
  ```bash
  curl http://localhost:8084/v3/api-docs.yaml -o contracts/openapi/profile-service.yaml
  ```
  Desde ese archivo el `mcp-gateway` genera su cliente Python.
- **Eventos (contract-first):** se escribe primero el schema y luego se generan los tipos:
  - Java: `jsonschema2pojo`
  - Node/TS: `json-schema-to-typescript`
  - Python: `datamodel-code-generator` (modelos Pydantic)

Si un cambio rompe compatibilidad (quitar o renombrar un campo obligatorio), se crea una versión nueva del mensaje en lugar de editar la existente.
