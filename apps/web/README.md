# web

Front React + Vite (antes `packages/aplicafacil-front`). Habla solo con el `api-gateway` (`http://localhost:8080/api/...`)
y se autentica contra auth-service (`http://localhost:9000`) con el cliente público `aplicafacil-web` (Authorization Code + PKCE, redirect `http://localhost:5173/callback`).

```bash
npm install
npm run dev        # http://localhost:5173 (puerto fijo: es el redirect registrado)
npm run build
```

Variables opcionales en `.env.local` (ver `.env.example`): `VITE_AUTH_ISSUER`, `VITE_AUTH_CLIENT_ID`, `VITE_API_URL`.

| Ruta | Qué hace | Backend |
|---|---|---|
| `/` | Iniciar sesión / crear cuenta (redirige a auth-service) | `/oauth2/authorize` |
| `/callback` | Recibe el `code` de auth-service y lo canjea (PKCE) | `/oauth2/token` |
| `/perfil` | Ver y editar la información personal | `GET /api/people/people/me`, `POST /api/people/people`, `PUT /api/people/people/{id}` |
| `/linkedin` | Conectar, listar y desconectar cuentas de LinkedIn | [`contracts/openapi/linkedin-accounts.yaml`](../../contracts/openapi/linkedin-accounts.yaml) (**propuesto, sin implementar**) |
| `/linkedin/callback` | Recibe el `code` de LinkedIn y lo envía al backend | `POST /api/people/linkedin/accounts` |

Notas de seguridad:

- Las contraseñas solo se escriben en las páginas de auth-service (ADR 0002). "Crear cuenta" abre el login de auth-service, que enlaza al registro y vuelve al mismo flujo OAuth.
- El access token (15 min) vive en `sessionStorage`. No hay refresh token para clientes públicos: al vencer se repite el redirect y, si la sesión de auth-service sigue viva, vuelve sin pedir contraseña.
- El token de LinkedIn nunca llega al navegador: el backend canjea el `code` con el client_secret y lo guarda cifrado.
