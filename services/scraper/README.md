# scraper (Node.js + NestJS + Puppeteer)

Solo ejecuta acciones en el navegador. **No tiene base de datos ni lógica de IA.**
Recibe comandos de RabbitMQ (`scraper.apply-job`, `scraper.search-jobs`) y publica eventos
(`aplicafacil.events`, routing keys `job.*`). Contratos en `contracts/`.

Estructura planeada:

```
src/
├── browser/        # BrowserManager, sesiones por cuenta, human-behavior
├── linkedin/
│   ├── login/
│   ├── job-search/
│   ├── job-detail/
│   ├── easy-apply/
│   └── captcha/
├── form/           # form.dom: leer el paso del formulario / ejecutar acciones
├── messaging/      # consumers de comandos, publishers de eventos (amqplib)
└── health/
```

Origen: `aplicafacil-server/src/linkedin-automation/components/*` del código anterior.
