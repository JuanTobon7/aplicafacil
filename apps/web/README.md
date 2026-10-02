# web

Front React + Vite (antes `packages/aplicafacil-front`). Habla solo con el `api-gateway` (`http://localhost:8080/api/...`)
y se autentica contra auth-service (`http://localhost:9000`) con el cliente público `aplicafacil-web` (Authorization Code + PKCE, redirect `http://localhost:5173/callback`).
