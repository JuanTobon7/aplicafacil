# AplicaFacil — instrucciones para agentes

Lee `CONSTRAINTS.md` antes de escribir código. No lo aflojes para que un cambio pase.

- Antes de entregar una tarea corre `.\quality.cmd` y arregla lo que marque (el código nuevo cumple los números de la
  tabla; el ratchet solo cubre lo heredado).
- Arquitectura y decisiones: `docs/adr/`. Cada servicio es hexagonal (`domain`, `application`, `infrastructure`, `api`).
- Contratos entre servicios: `contracts/` (JSON Schema, AsyncAPI, OpenAPI). Si cambias un mensaje o un endpoint,
  cambia primero el contrato.
