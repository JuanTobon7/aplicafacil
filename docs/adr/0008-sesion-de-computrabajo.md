# ADR 0008 — Sesión de candidato de Computrabajo (computrabajo-service)

- **Estado:** aceptada
- **Fecha:** 2026-10-25
- **Decisores:** Juan Tobón
- **Relacionada con:** [ADR 0003](0003-identidad-linkedin-y-sesion-local.md) (sesión cifrada, inicio de sesión manual),
  [ADR 0004](0004-scraper-como-implementacion-del-navegador.md) (el navegador vive en el scraper),
  [ADR 0006](0006-worker-de-busqueda-de-vacantes.md) (búsqueda pública en Computrabajo)

## Contexto

Buscar en Computrabajo no necesita sesión, pero postular, ver las postulaciones y la hoja de vida sí. La persona quiere
quedar con LinkedIn y Computrabajo iniciados y que, al abrir la app, se le pida iniciar sesión solo si la sesión venció.

Opciones: (a) generalizar `linkedin-service` a "sesiones de portales"; (b) un servicio nuevo dueño de la sesión de
Computrabajo. LinkedIn además es el registro y el login de AplicaFácil (crea cuentas, sincroniza el perfil); Computrabajo
no: solo se conecta a una cuenta que ya existe.

## Decisión

Servicio nuevo **computrabajo-service** (:8087, schema `computrabajo`), hexagonal como los demás:

```
 Front (Mi perfil) ──/api/computrabajo/**──▶ computrabajo-service ──HTTP + token de servicio──▶ scraper
                                              │ ConnectService (intento en memoria, polling)     /portals/computrabajo/sessions/login (NDJSON)
                                              │ SessionCheckService (reusa 10 min)              /portals/computrabajo/sessions/validate
                                              ▼
                                   computrabajo_session (estado + storageState AES-256-GCM)
```

- **Conectar:** `POST /computrabajo/connect` abre la ventana visible en `candidato.{país}.computrabajo.com/acceso/`
  (también con Google). La persona inicia sesión; el scraper detecta la cookie `AspNet.SharedCookie` fuera de las
  páginas de acceso, lee el nombre de la cabecera y devuelve el storageState. El servicio lo cifra y lo guarda.
- **Al abrir la app:** `POST /computrabajo/session/check`. Una validación de hace menos de 10 minutos se reusa; si no,
  se valida headless y se renueva el storageState, o la conexión queda `EXPIRED` y el front muestra el aviso.
- **Desconectar y `user.deleted`:** se borra la fila (cola `computrabajo.user-deleted`, RabbitMQ).
- La sesión nunca pasa por RabbitMQ ni se loguea; el scraper no la guarda.

## Consecuencias

- La conexión de LinkedIn sigue intacta y con su flujo especial de registro; Computrabajo no lo complica.
- Un portal nuevo con sesión copia este servicio (o, si llegan varios, se extrae una librería común de
  conexión/validación).
- Pendiente: leer la hoja de vida y las postulaciones con la sesión guardada (falta capturar esas páginas) y usar la
  sesión en las búsquedas y postulaciones de Computrabajo.
