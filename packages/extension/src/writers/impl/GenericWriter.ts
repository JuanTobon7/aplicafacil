import { BaseWriter } from "./BaseWriterImpl";

// ------------------------------------------------------------------
// Writer genérico: funciona en cualquier sitio, escribe por id directo.
// Úsalo como fallback cuando no hay un writer específico registrado.
// ------------------------------------------------------------------

export class GenericWriter extends BaseWriter {
  available(): boolean {
    return true;
  }
}