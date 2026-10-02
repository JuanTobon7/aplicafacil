# mcp-gateway (Python)

Servidor MCP que expone las capacidades de los microservicios Java como **tools** para LLMs.

- Funciona como **OAuth 2.0 Resource Server**: valida tokens de auth-service con `aud = mcp-gateway`
  y publica `/.well-known/oauth-protected-resource` (RFC 9728).
- **No reenvía** el token del cliente a Java (la especificación MCP prohíbe el token passthrough):
  hace token exchange (RFC 8693) en `http://auth-service:9000/oauth2/token` con el cliente `mcp-gateway`
  para obtener un token con `aud = aplicafacil-api`.
- Las tools se diseñan por tarea y el usuario sale del token, nunca de un `userId` en los argumentos.

Stack previsto: `uv`, SDK oficial `mcp` (FastMCP), `httpx`, `pydantic`, `pyjwt`.

```
src/aplicafacil_mcp/
├── auth/          # validación de JWT, token exchange, metadata RFC 9728
├── clients/       # people_client.py, profile_client.py, jobs_client.py (generados desde contracts/openapi)
├── tools/
├── resources/
├── prompts/
├── config.py
└── server.py
```
