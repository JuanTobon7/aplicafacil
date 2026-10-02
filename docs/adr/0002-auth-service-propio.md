# ADR 0002: auth-service propio en lugar de Keycloak

- **Estado:** aceptada (reemplaza a Keycloak como Authorization Server de la ADR 0001)
- **Fecha:** 2026-10-01

## Contexto

La primera versión usaba Keycloak como servidor OAuth 2.0. Se decidió tener un microservicio de
autenticación propio en Java, para controlar el modelo de usuarios, el registro y la integración
con el resto de servicios (por ejemplo, crear la `Person` al registrarse).

## Decisión

`auth-service` es el **Authorization Server OAuth 2.1 / OpenID Connect**, construido con Spring
Authorization Server (integrado en Spring Security 7.1).

| Cliente | Tipo | Grant | Uso |
|---|---|---|---|
| `aplicafacil-web` | público | Authorization Code + PKCE (S256 obligatorio) | front web y extensión |
| `automation-service` | confidencial | `client_credentials` | llamadas entre servicios |
| `mcp-gateway` | confidencial | token exchange (RFC 8693) | cambia el token del usuario por uno con `aud = aplicafacil-api`, sin reenviarlo |

- **Tokens:** JWT RS256 que duran 15 minutos. Claims: `sub` = id del usuario (UUID estable, no el email),
  `aud = aplicafacil-api`, `roles` (`USER`, `ADMIN`; `SERVICE` en tokens de servicio), `email` y `scope`.
- **Estado en Postgres** (schema `auth`): usuarios, clientes, autorizaciones, consentimientos y
  **llaves de firma**. Los tokens sobreviven reinicios y el servicio puede tener varias instancias.
  Las llaves se generan solas la primera vez, protegidas con un advisory lock.
- **Contraseñas:** solo se escriben en las páginas de login y registro de auth-service, nunca en el front.
  Se guardan con BCrypt mediante `DelegatingPasswordEncoder`. Política: 8 a 72 caracteres, con letras y
  números, y sin contener el email.
- **Fuerza bruta:** después de 5 intentos fallidos la cuenta se bloquea 15 minutos.
- **Registro:** al confirmarse la transacción se publica `user.registered` en RabbitMQ y people-service crea la
  `Person` (`Person.userId = sub`) de forma idempotente.
- **Validación en los servicios:** todos validan firma (JWKS), `iss` y `aud`. El navegador ve el emisor
  `http://localhost:9000`; dentro de Docker las llaves se descargan desde `http://auth-service:9000/oauth2/jwks`.

## Consecuencias

- (+) Un solo lenguaje y un solo modelo para la identidad; el registro está integrado con el dominio.
- (+) Se elimina un componente externo pesado (Keycloak y su base de datos).
- (−) La seguridad del login depende de nuestro código. Lo mitigan los tests de integración
  (`OAuth2FlowsIntegrationTest`) que cubren todos los flujos.
- (−) Pendiente para producción:
  - Cifrar la llave privada en reposo (KMS o Vault) y rotar llaves.
  - Sesiones compartidas (Spring Session) si hay varias instancias detrás de un balanceador.
  - Verificación de email y recuperación de contraseña (requieren un servicio de correo).
  - Transactional outbox para `user.registered`: hoy, si RabbitMQ no está disponible justo después
    del commit, el evento se pierde y queda solo en el log.
  - Refresh tokens para el cliente público (Spring no los emite a clientes públicos; alternativa: un BFF).
