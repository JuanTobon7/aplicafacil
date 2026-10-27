# Postulación: recorrido manual y selectores (Computrabajo y LinkedIn)

Recorrido hecho a mano el **2026-10-23** en el navegador para replicarlo en el scraper (`services/scraper`) y en la
automatización de postulaciones. Todo lo de abajo se vio en vivo; lo que no se pudo ver está marcado como
**sin validar**. Cuando un selector deje de funcionar, se corrige aquí y en el `*.selectors.ts` del portal.

> Hasta dónde llegó: detalle de la oferta y botón de postular en ambos portales, y la pantalla de acceso. Postular
> exige la sesión de la persona en el portal; no se escribieron credenciales, así que el formulario de postulación
> (preguntas, CV, enviar) queda **sin validar**.

## 0. En AplicaFácil (para tener vacantes guardadas)

1. `http://127.0.0.1:8080` → "Continuar como …" (cuenta local ya conectada) → `/perfil`.
2. `/vacantes` → Portal **Computrabajo**, palabras clave `desarrollador java spring boot`, "Publicadas hace como mucho"
   **7 días**, **Solo remoto** → Buscar.
3. El scraper abrió `https://co.computrabajo.com/trabajo-de-desarrollador-java-spring-boot-en-remoto?pubdate=7` y
   guardó 3 ofertas (`jobs.job_search.remote = true`, `posted_within_days = 7`).
4. Las ofertas guardadas están en `jobs.job_posting` (`platform`, `external_id`, `url`). El `external_id` de
   Computrabajo es el mismo id que usa su postulación (`oi=` abajo).

## 1. Computrabajo

### 1.1 Listado (ya implementado en `computrabajo.selectors.ts`)

| Qué | URL / selector |
|---|---|
| Búsqueda | `/trabajo-de-{slug}` · `-en-{ciudad}` · remoto: `-en-remoto` (sin ciudad) · híbrido: `-hibrido` |
| Fecha | `?pubdate=1\|3\|7\|15\|30` (hoy, 3 días, semana, 15 días, mes) · urgente: `?pubdate=99` |
| Página | `&p=N` (20 por página) |
| Card | `article.box_offer[data-id]` |
| Filtro remoto en la UI | `ul.box_checkbox[data-id=idworkplace] input#remote[data-url][data-sem="-en-remoto"]` |

### 1.2 Detalle de la oferta

URL guardada: `https://co.computrabajo.com/ofertas-de-trabajo/oferta-de-trabajo-de-{slug}-{EXTERNAL_ID}`

| Campo | Selector | Ejemplo |
|---|---|---|
| Título | `h1.box_detail` (clases vistas: `fwB fs24 mb5 box_detail w100_m`) | "Desarrollador Backend remoto - Remoto" |
| Empresa y lugar | `h1 + p.fs16` | "Mainsoft LTDA - Bogotá, D.C., Bogotá, D.C." |
| Ya postulado (banner) | `h1 ~ div.box_detail.hide` → sin `hide` si ya aplicó | "Ya aplicaste a esta oferta" |
| Contenedor | `div.box_detail div.mb40.pb40.bb1` (el que contiene `h2` "Descripción de la oferta") | |
| Etiquetas (salario, contrato, jornada, modalidad) | contenedor `> div.mbB span.tag` | "A convenir", "Contrato de Obra o labor", "Tiempo Completo", "Remoto" |
| Descripción | contenedor `> p.mbB` | texto libre |
| Requisitos | contenedor `> ul.disc.mbB > li` | "Educación mínima: …", "3 años de experiencia", "Idiomas: Inglés", "Conocimientos: Java, …" |
| Aptitudes (skills) | `span.tag[data-skill-id]` | Java (`A8DD0C9F643B47A8`), Javascript, Microservicios, Node.js |
| Palabras clave | contenedor `> p.fc_aux.fs13.mbB.mtB` | "Palabras clave: developer, …" |
| Antigüedad | contenedor `> p.fc_aux.fs13` (el último) | "Ayer (actualizada)" |

Las aptitudes con `data-skill-id` sirven para comparar la oferta con las `Skill` del perfil (ADR 0007) sin
interpretar texto.

### 1.3 Botón de postular

Hay **dos** botones iguales (cabecera y barra fija inferior en móvil): usar el primero visible.

| Qué | Selector / valor |
|---|---|
| Aplicar | `a.b_primary.big[data-href-offer-apply]` (texto "Aplicar") |
| Destino | `data-href-offer-apply="https://candidato.co.computrabajo.com/match/?oi={EXTERNAL_ID}&p=57&idb=1&d=32"` |
| Destino sin sesión | `data-href-access` (misma URL sin `&d=32`) |
| Ya postulado | `a[already-applied-btn].postulated` (texto "Postulado"; tiene `hide` mientras no se aplicó) |

`oi` es el `external_id` guardado: se puede ir directo a la URL de `match` sin abrir el detalle. `p=57` es el país
(Colombia) e `idb=1`/`d=32` son de origen o seguimiento (**sin validar** qué cambian).

### 1.4 Sin sesión: acceso de candidato

Clic en "Aplicar" → `https://secure.computrabajo.com/Account/Login?ReturnUrl=…` (vuelve a la oferta después de entrar).

| Qué | Selector |
|---|---|
| Formulario | `form#loginForm` (POST, oculto `__RequestVerificationToken`, `ReturnUrl`) |
| Correo | `input#Email[name=Email]` → botón `button#continueWithMailButton` ("Continuar") |
| Contraseña (segundo paso) | `input#password[name=Password]` |
| Con Google | `a#js_google_access` (popup de Google: no se puede automatizar) |
| Recordar contraseña | `input#rememberPasswordEmail` |

Pasos observados: 1) correo → Continuar, 2) aparece la contraseña, 3) envía y vuelve a `ReturnUrl`.

### 1.5 Con sesión: formulario de postulación — **sin validar**

Falta ver `candidato.co.computrabajo.com/match/?oi=…` con sesión iniciada: si pide preguntas de filtro, carta, qué
CV adjunta y cuál es el botón final. Es el siguiente recorrido (ver "Pendiente").

## 2. LinkedIn

### 2.1 Detalle (vista pública, sin sesión)

URL guardada: `https://www.linkedin.com/jobs/view/{EXTERNAL_ID}/`

| Campo | Selector |
|---|---|
| Título | `h1.top-card-layout__title` |
| Descripción | `div.description__text.description__text--rich` |
| Criterios (antigüedad, tipo de empleo, sectores) | `ul.description__job-criteria-list > li.description__job-criteria-item` |
| Sesión iniciada | sin sesión no existe `#global-nav` (con sesión la página es otra: `jobs-unified-top-card`, **sin validar**) |

### 2.2 Botón de postular

| Qué | Selector |
|---|---|
| Solicitar (Easy Apply) | `button#topbar-apply.apply-button[data-tracking-control-name=public_jobs_apply-link-onsite]` (texto "Solicitar") |
| Mismo botón en la tarjeta | `button.apply-button.top-card-layout__cta` |
| En el sitio de la empresa | `data-tracking-control-name=public_jobs_apply-link-offsite` (**sin validar**: no apareció en las ofertas vistas) |

`onsite` = Easy Apply dentro de LinkedIn (`job_posting.quick_apply`); `offsite` = redirige al sitio de la empresa.
Sin sesión, "Solicitar" abre el modal de acceso (`button.contextual-sign-in-modal__sign-in-with-email-cta`).

### 2.3 Con sesión: modal de Easy Apply — **sin validar**

Lo maneja `linkedin-service` con la sesión cifrada del usuario (ADR 0003/0004), no este navegador. Falta mapear
los pasos del modal (contacto, CV, preguntas, revisar, enviar).

## 3. Cómo llevarlo a la automatización

1. **Sesión por portal, como LinkedIn.** Computrabajo necesita su propia sesión: replicar el flujo "Conectar"
   de ADR 0003 (ventana visible, la persona entra, el scraper guarda el `storageState` cifrado). La contraseña y
   Google nunca pasan por la app.
2. **Ir directo a postular:** `candidato.{país}.computrabajo.com/match/?oi={external_id}&p={país}&idb=1` con el
   `storageState`; si redirige a `secure.computrabajo.com/Account/Login`, la sesión venció → `SESSION_EXPIRED`.
3. **Idempotencia:** antes de enviar, si `a[already-applied-btn]` no tiene `hide` (o aparece "Ya aplicaste a esta
   oferta"), marcar la postulación como hecha sin volver a enviar.
4. **Datos del detalle para puntuar** antes de postular: etiquetas (modalidad, contrato), requisitos y
   `span.tag[data-skill-id]` contra las skills del perfil.
5. **Enviar siempre con confirmación de la persona** (o una regla explícita suya): postular es irreversible.

## Pendiente

- Recorrer con sesión iniciada el `match` de Computrabajo y el modal de Easy Apply hasta antes de "Enviar", y
  completar 1.5 y 2.3. Para eso la persona tiene que iniciar sesión ella misma en el navegador.
