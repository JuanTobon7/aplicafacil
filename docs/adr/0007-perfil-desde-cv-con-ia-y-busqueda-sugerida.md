# ADR 0007 — Perfil laboral desde el CV con IA (vía mcp-gateway) y búsqueda de empleo sugerida

- **Estado:** propuesta
- **Fecha:** 2026-10-23
- **Decisores:** Juan Tobón
- **Relacionada con:** [ADR 0003](0003-identidad-linkedin-y-sesion-local.md) (perfil desde LinkedIn),
  [ADR 0005](0005-mcp-gateway-y-proveedores-de-ia.md) (mcp-gateway y proveedores de IA),
  [ADR 0006](0006-worker-de-busqueda-de-vacantes.md) (búsqueda de vacantes)

## Contexto

El perfil solo se llenaba desde LinkedIn, y el lector de LinkedIn todavía no lee las secciones (etapa 2). La forma
más directa de tener un perfil completo es el CV de la persona. Además, buscar vacantes exigía escribir las palabras
clave a mano, sin filtro de modalidad (remoto) en ningún portal ni de fecha en Computrabajo.

El perfil guardaba experiencia, educación, skills e idiomas como arrays JSONB dentro de `user_profile` (ADR 0003).
Para trabajar con cada skill o experiencia (mostrarlas, editarlas una por una, usarlas para elegir y luego para
puntuar vacantes) hace falta que sean entidades con identidad propia.

## Decisión

### 1. Entidades del perfil

`Skill` (nombre, categoría, nivel, años), `Experience` (cargo, empresa, periodo, descripción, tecnologías),
`Education`, `Language` y `Certification` son entidades del dominio con `id` propio. `UserProfile` es la raíz:
decide qué copia de una lectura (LinkedIn o CV) y qué conserva — **lo editado a mano no se pisa con ninguna de las
dos** — y `ProfileContent` une en memoria las skills e idiomas repetidos (mapa por nombre sin mayúsculas).

En la BD (V4) cada una es una tabla (`profile_skill`, `profile_experience`, …) con `user_id` → `user_profile` y
`position` para el orden; `ON DELETE CASCADE` las borra con el perfil. Los datos que ya había en los arrays JSONB se
copian en la migración. JPA las mapea como `@OneToMany` + `@OrderColumn`, LAZY y cargadas por lotes
(`default_batch_fetch_size`); al guardar, las filas cuyo id sigue en el dominio se actualizan en su lugar.

### 2. Importar un CV

```
 Front ─POST /profiles/me/cv (multipart: file, configId)─▶ profile-service
        Tika saca el texto en memoria (el archivo no se guarda) · ai_task PENDING · outbox
                     ─ ai.request.submit ─▶ [aplicafacil.commands] ─▶ mcp-gateway ─▶ proveedor del usuario
                     ◀ ai.request.completed|failed ─ [aplicafacil.events] ─ profile.ai-results (cola propia)
        JSON → ProfileContent → UserProfile.importCv → ai_task COMPLETED → pide la búsqueda sugerida
```

- Se reutiliza el comando `ai.request.submit` existente y el mcp-gateway no cambia: el prompt y la forma del JSON
  viven en profile-service (`ProfilePrompts`), y el mcp-gateway sigue sin tools genéricas.
- El mcp-gateway valida que `configId` sea del `userId` (registro por usuario); un proveedor ajeno o apagado vuelve
  como `ai.request.failed` y la tarea queda `FAILED` con el mensaje.
- Cada servicio que pide IA tiene su cola ligada a `ai.request.completed|failed` y aplica solo los `requestId` que
  creó; ai-config-service ya ignoraba los ajenos. Idempotente: un resultado repetido o tardío no reabre la tarea.
- Una tarea pendiente por persona; vence a los 3 min (`aplicafacil.profile.ai.timeout`).
- La respuesta se lee de forma tolerante (JSON dentro de texto o de un bloque de código, fechas `2021`, `03/2021`,
  enums en minúsculas). Una entrada incompleta se descarta; una respuesta sin JSON deja la tarea `FAILED`
  (`INVALID_OUTPUT`) sin tocar el perfil.
- Solo se reemplazan las secciones que el CV trae: un CV sin idiomas no borra los idiomas que ya había.

### 3. Búsqueda sugerida y configurable

Al terminar la extracción, profile-service pide a la IA (mismo proveedor) la búsqueda clave para el perfil
("desarrollador backend java spring boot"), con hasta 5 alternativas y el motivo. Se guarda en el perfil
(`search_*`) junto con lo que decide la persona: **solo remoto**, **antigüedad máxima de la oferta** (1–30 días) y
lugar. Una sugerencia nueva cambia las palabras clave pero no esas opciones. "Buscar vacantes" abre `/vacantes` con
todo precargado; jobs-service recibe `remote` y `postedWithinDays` como cualquier búsqueda.

| Filtro | LinkedIn | Computrabajo (validado en vivo el 2026-10-23) |
|---|---|---|
| Remoto | `f_WT=2` | `/trabajo-de-{q}-en-remoto` (sin lugar: una oferta remota no tiene ciudad) |
| Publicada hace ≤ N días | `f_TPR=r{N·86400}` | `?pubdate=1\|3\|7\|15\|30`: el menor que cubra N |

### 3.1 "Buscar con IA": la IA también lanza las búsquedas (2026-10-27)

`POST /profiles/me/search-plan` crea una tarea `SEARCH_PLAN` con el mismo prompt que la sugerencia. Cuando la IA
responde, la sugerencia queda en el perfil y, en la misma transacción, `profile.search-plan.ready` va al outbox
(contracts/schemas/search-plan-ready-event.schema.json). jobs-service lo consume (`jobs-service.search-plans`, con DLQ)
y encola la búsqueda principal y una alternativa en cada portal (LinkedIn primero), con 25 ofertas cada una y el
remoto/fecha/lugar del perfil. Son 4 búsquedas: caben en el tope de 5 en curso por persona; si no hay cupo se lanzan las
que quepan. `job_search.plan_id` (= id de la tarea) hace idempotente un evento repetido y marca las búsquedas "IA" en
el front.

### 4. Rendimiento (búsquedas anidadas)

- `JdbcJobPostingStore.saveAll` hacía dos consultas por oferta (hasta 400 por búsqueda). Ahora son dos por búsqueda:
  un `INSERT … VALUES (…), (…) ON CONFLICT … RETURNING` para todas las ofertas y un lote JDBC para los vínculos; el
  id de cada oferta se cruza en memoria por `external_id`, sin subconsultas.
- Las secciones faltantes de LinkedIn se buscan en un `Set` (antes, `List.contains` por cada sección) y las skills se
  agrupan en una sola pasada con un mapa (backend y front).
- Borrar un perfil es un solo `DELETE` (antes, leer y luego borrar); las hijas las borra la BD.

## Consecuencias

- (+) El perfil queda completo con un archivo, sin esperar al lector de LinkedIn, y la búsqueda sale sola.
- (+) Skills y experiencias con identidad: base para editarlas una por una y para puntuar vacantes contra el perfil.
- (+) Sin infraestructura nueva: RabbitMQ, outbox y mcp-gateway que ya existían.
- (−) El texto del CV viaja por RabbitMQ (mensaje persistente en disco) y llega al proveedor de IA que eligió la
  persona. Es su dato y su proveedor, pero queda escrito así en la pantalla de importación.
- (−) La calidad depende del modelo. El prompt pide no inventar y el lector descarta lo incompleto, pero un modelo
  pequeño puede omitir secciones; se puede reimportar o pedir otra sugerencia.
- (−) Un PDF escaneado (sin capa de texto) no se puede leer: responde `CV_UNREADABLE` (no hay OCR).
- Pendiente: editar cada entidad desde el front (las secciones editadas a mano ya se respetan) y usar el perfil para
  puntuar las vacantes encontradas.
