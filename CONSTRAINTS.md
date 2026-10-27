# Constraints

Último repaso: 2026-10-19 por @JuanTobon7. Las métricas nuevas **avisan hasta el 2026-10-30** y **bloquean desde esa
fecha**; el piso bloquea desde hoy.

Este archivo es el listón del repo: lo que tiene que cumplir un cambio para entregarse. Cada número tiene su motivo y
el comando que lo verifica. Endurecerlo es libre; aflojarlo (subir un umbral, quitar una fila, agregar una excepción) va
en su propio commit y se discute en el PR. `tools/quality/floor-guard.mjs` lo detecta.

```bash
.\quality.cmd            # al terminar una tarea: piso + tamaño + acoplamiento + pruebas de lo cambiado (≤ 5 min)
.\quality.cmd -Fast      # mientras se edita: piso + linters (segundos)
.\quality.cmd -Full      # antes de integrar: todo, incluida seguridad
```

## Piso (siempre, bloquea desde hoy)

- Sin supresiones nuevas: `@SuppressWarnings`, `// NOPMD`, `# noqa`, `# type: ignore`, `pragma: no cover`, `@ts-ignore`, `oxlint-disable`
- Sin trabajo a medias: `TODO`, `FIXME`, `raise NotImplementedError`, `throw new UnsupportedOperationException("no implementado")`, `catch {}` vacío, `except: pass`
- Sin pruebas más fáciles: `@Disabled`, `.skip(`, `pytest.skip`, ni borrar una prueba o una aserción de una prueba que sigue
- Sin secretos en el código
- Este archivo no se afloja para que un cambio pase

Lo verifica `node tools/quality/floor-guard.mjs` sobre el diff contra `origin/main` (archivos nuevos incluidos).

## Con números

| Dimensión | Regla | Por qué este número | Lo verifica | Corre en |
|-----------|-------|---------------------|-------------|----------|
| Líneas por función (Java) | método ≤ 30 sentencias (NCSS), clase ≤ 300 | 30 sentencias caben en una pantalla; más casi siempre son dos responsabilidades | PMD `NcssCount` (`services/backend/config/pmd-quality.xml`) | tarea |
| Líneas por función (TS) | función ≤ 40 líneas; componente `.tsx` ≤ 80; archivo ≤ 300 | el JSX duplica las líneas sin sumar lógica, por eso el componente tiene más margen | oxlint `max-lines-per-function`, `max-lines` (`.oxlintrc.json`) | edición |
| Líneas por función (Python) | función ≤ 40 sentencias | mismo criterio que Java, contado como lo cuenta Ruff | Ruff `PLR0915` (`pyproject.toml`) | edición |
| Complejidad ciclomática | ≤ 10 por función/método | más de 10 caminos ya no se prueban todos; es el umbral clásico de McCabe | PMD `CyclomaticComplexity`, oxlint `complexity`, Ruff `C901` | edición / tarea |
| Complejidad cognitiva (Java) | ≤ 15 por método | penaliza anidamiento y saltos, que es lo que cuesta leer | PMD `CognitiveComplexity` | tarea |
| Parámetros | Java ≤ 6, Python ≤ 6, TS ≤ 4 | más parámetros piden un objeto; TS usa objetos con nombre de forma natural | PMD `ExcessiveParameterList`, Ruff `PLR0913`, oxlint `max-params` | edición / tarea |
| Anidamiento | ≤ 3 bloques (TS), ≤ 4 `if` (Java), ≤ 12 ramas (Python) | cada nivel duplica los casos a pensar | oxlint `max-depth`, PMD `AvoidDeeplyNestedIfStmts`, Ruff `PLR0912` | edición / tarea |
| Acoplamiento por clase (Java) | ≤ 20 tipos usados por clase; ≤ 35 imports por archivo | más dependencias = más motivos para cambiar; 20 es el umbral de PMD | PMD `CouplingBetweenObjects`, `ExcessiveImports`, `GodClass` | tarea |
| Capas hexagonales (Java) | domain puro; application no conoce infrastructure ni api; infrastructure no conoce api; api pasa por application; sin ciclos | ADR 0001 regla 4: los casos de uso dependen de puertos, no de adaptadores | ArchUnit `HexagonalRules` (`ArchitectureTest` de cada servicio) | tarea |
| Inversión de dependencias (Java) | application no usa Spring Data, JPA, JDBC, Hibernate, clientes HTTP ni AMQP | SOLID-D: si un caso de uso necesita una de esas cosas, le falta un puerto | ArchUnit `applicationUsesPortsNotTechnology` | tarea |
| Segregación de interfaces (Java) | un puerto (`application.port..`) tiene como máximo 8 métodos | SOLID-I: más de 8 casi siempre sirven a varios clientes distintos; se parte por cliente | ArchUnit `portsAreSmall` | tarea |
| Capas (TypeScript, scraper) | domain ← application ← infrastructure/api; los portales no conocen Playwright | igual que Java; el motor del navegador es reemplazable | `test/architecture.test.ts` (vitest) | tarea |
| Capas (Python) | domain/contracts ← providers ← application ← adaptadores ← container ← asgi; el dominio no importa frameworks ni SDK | ADR 0005: la orquestación no conoce SDK concretos | import-linter (`lint-imports`) | tarea |
| Secretos | ningún hallazgo | una key en git es una key filtrada | `gitleaks dir . --config .gitleaks.toml --redact` | completo |
| Dependencias (npm, Python) | nada de severidad alta o mayor | por debajo de alta casi todo es ruido | `osv-scanner scan source --lockfile=…` | completo |
| Pruebas | todas pasan en los módulos cambiados (Testcontainers incluido) | lo que se toca se prueba antes de entregarlo | `mvnw test`, `vitest run`, `pytest` | tarea |

Las filas de tamaño, complejidad y acoplamiento son **ratchets**: hoy hay violaciones heredadas, registradas en
`tools/quality/baseline.json`, y `node tools/quality/ratchet.mjs` falla si alguna cuenta crece. **El código nuevo debe
cumplir el número de la tabla**; el ratchet solo protege de empeorar lo que ya existía. Al corregir una violación,
`ratchet.mjs --update` baja la línea base (nunca la sube).

Las reglas de ArchUnit **no tienen deuda**: el 2026-10-19 se corrigieron todas las violaciones heredadas y dejaron de
estar congeladas. Cualquier violación falla.

## Diseño: SOLID y GRASP

Son reglas, no sugerencias. Las que se pueden medir están en la tabla de arriba y bloquean; el resto se revisa en cada
PR con esta lista. Cada principio dice cómo se ve en este repo.

### SOLID

| Principio | En este repo | Lo verifica |
|-----------|--------------|-------------|
| **S**: una razón para cambiar | Una clase sirve a un actor. Si el constructor pasa de 6 dependencias o la clase de 20 tipos usados, son dos clases (`ConnectService` inicia el intento; `ConnectionCompleter` hace lo que sigue al login) | PMD `GodClass`, `CouplingBetweenObjects`, `ExcessiveParameterList`, `NcssCount`; oxlint `max-lines` |
| **O**: abierto a extensión | Un portal o proveedor nuevo es una clase nueva registrada, sin tocar las existentes (`JobPortalGateways`, `ProviderFactory`). Un `switch` sobre el tipo en application es una estrategia esperando | revisión; `complexity` ≤ 10 lo hace visible |
| **L**: sustituibles | Toda implementación de un puerto con más de una implementación pasa la misma suite de contrato (`job-portal.contract.ts`, `test_provider_contract.py`) | revisión: puerto nuevo con 2+ implementaciones ⇒ suite de contrato |
| **I**: interfaces chicas | Un puerto por necesidad del cliente: `AccountProvisioning`, `ProfileSync`, `CredentialVault`; no un "cliente de identity-service" con todo | ArchUnit `portsAreSmall` (≤ 8 métodos) |
| **D**: depender de abstracciones | application declara el puerto y habla en términos del dominio; infrastructure lo implementa (JPA, HTTP, RabbitMQ). La configuración llega como un record de application (`AiPolicy`, `TokenPolicy`, `ConnectPolicy`), nunca como `*Properties` | ArchUnit `applicationDoesNotKnowAdapters`, `applicationUsesPortsNotTechnology` |

### GRASP

| Patrón | En este repo | Señal de que falta |
|--------|--------------|--------------------|
| **Experto en información** | La regla vive con los datos: `UserProfile.sync`, `LinkedInConnection.connect`, `ProviderConfig.applyStatus`, `AppUser.apply` | un servicio que lee y escribe campos de otro objeto para decidir algo que ese objeto podría decidir |
| **Creador** | El agregado crea sus instancias válidas: `ProviderConfig.create`, `AiRequest.submit`, `LinkedInConnection.disconnected` | `new Entidad()` y diez setters en un servicio |
| **Controlador** | Los `@RestController` y `@RabbitListener` traducen y delegan en un caso de uso; no deciden | lógica de negocio o acceso a datos en `api/` (ArchUnit `apiGoesThroughApplication`) |
| **Bajo acoplamiento** | Dependencias solo hacia dentro (api → application → domain); entre servicios, contratos de `contracts/` | `CouplingBetweenObjects` > 20, ciclos (ArchUnit `noCycles`) |
| **Alta cohesión** | Una clase hace una cosa y sus métodos usan sus campos | PMD `GodClass`; grupos de métodos que no comparten campos |
| **Polimorfismo** | Variantes por tipo como implementaciones (Strategy/Registry), no como ramas | el mismo `switch`/`if` sobre un tipo en varios lugares |
| **Fabricación pura** | Lo que no es del dominio va en una clase hecha para eso: mapeadores (`ProviderConfigMapper`), `LoginStream`, `ScraperClients` | entidades de dominio que saben serializarse o guardarse |
| **Indirección** | Los puertos de `application/port/out` separan el caso de uso de la tecnología | application importando un adaptador |
| **Variaciones protegidas** | Lo que cambia (portales de empleo, proveedores de IA, motor del navegador) está detrás de un puerto con suite de contrato | cambiar de proveedor obliga a tocar casos de uso |

## Medido, todavía no exigido

| Métrica | Hoy (2026-10-19) | Dirección |
|---------|------------------|-----------|
| Violaciones de tamaño/complejidad/acoplamiento | 1 (eran 33 el 2026-10-19): `generate_ai_response` del mcp-gateway tiene 8 parámetros porque son el esquema público de la tool MCP; agruparlos rompería a los clientes | no puede crecer |
| ArchUnit (capas, ciclos, DIP, ISP) | 0 en los 6 servicios (las heredadas se corrigieron el 2026-10-19) | ya se exige |
| Dependencias Java con vulnerabilidades altas o críticas | 10 paquetes (tomcat-embed-core 9.8, freemarker 9.1, amqp-client 8.7, jsoup 8.7, jackson 7.5…) vía el BOM de Spring Boot 4.1.1 | no puede crecer; se corrige subiendo Spring Boot |

Las dependencias Java se miden con un SBOM: `./mvnw org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom
-DoutputFormat=json -DoutputName=bom.cdx` y `osv-scanner scan source --lockfile=target/bom.cdx.json` (incluido en
`-Full`).

## Excepciones

| ID | Regla | Ruta | Motivo | Dueño | Vence |
|----|-------|------|--------|-------|-------|
| W1 | capas-python | `services/mcp-gateway/src/aplicafacil_mcp/web/views.py` | Las vistas de Django no admiten inyección por constructor: piden el contenedor (`ignore_imports` en pyproject) | @JuanTobon7 | 2027-01-17 |
| W2 | silenced-checker | `services/backend/linkedin-service/**/LocalPlaywrightSessionProvider.java` | `@SuppressWarnings("unchecked")` en el cast genérico de `LinkedInAction<R>`; Java no puede comprobarlo en tiempo de compilación | @JuanTobon7 | 2027-01-17 |
| W3 | test-made-easier | `services/mcp-gateway/tests/test_rabbitmq_integration.py` | Se omite (no se borra) cuando no hay Docker; con Docker corre siempre | @JuanTobon7 | 2027-01-17 |

## Cómo escribir código que pase a la primera

1. **Una función, una razón para cambiar.** Si necesitas un comentario "paso 1 / paso 2" dentro de una función,
   son dos funciones. Extrae antes de llegar al límite, no después.
2. **Parámetros: agrupa los que viajan juntos** en un record/objeto con nombre (`SearchCriteria`, `Settings`). Si
   cuatro métodos reciben los mismos tres valores, ese objeto ya existe y le falta nombre.
3. **Menos ramas, más polimorfismo.** Un `switch` por tipo de proveedor/portal es una estrategia esperando: registro
   + implementación por caso (como `JobPortalGateways` o `ProviderFactory`).
4. **Retornos tempranos en vez de anidar.** Valida y sal (`if (!ok) return …`); el camino feliz queda sin sangría.
5. **Los casos de uso dependen de puertos.** En `application/port/out` declara una interfaz que hable en términos del
   dominio (`UserProfileRepository`, `CredentialVault`) y deja la implementación (JPA, JDBC, HTTP) en
   `infrastructure/`. La entidad JPA nunca sale de infrastructure: el adaptador traduce (`toDomain` / `copy`).
6. **Los controladores no tocan la BD ni deciden.** `api/` → `application/` → puerto. Devolver una proyección de
   Spring Data desde un controlador acopla el contrato HTTP al esquema. Application lanza sus propias excepciones
   (`JobSearchRejectedException`); el código HTTP lo elige `api/`.
7. **Componentes React chicos.** Si un componente pasa de 80 líneas, separa la lógica en un hook
   (`useJobSearches`) y la vista en subcomponentes (formulario, lista, resultados).
8. **Prueba primero el contrato.** Una suite de contrato compartida (como `job-portal.contract.ts` o
   `test_provider_contract.py`) vale más que diez pruebas de cada implementación.
9. **Nunca silencies un check para pasar.** Si una regla está mal para un caso, va como excepción con dueño y fecha.
10. **La regla va con los datos (Experto).** Antes de escribir un `if` sobre los campos de otro objeto, pregúntate si
    ese objeto debería tener el método. Así se prueba sin Spring (`UserProfileTest`, `ProviderConfigTest`).
11. **Mide antes de entregar:** `.\quality.cmd`. Si algo empeora, arréglalo ahí, no en el PR.
