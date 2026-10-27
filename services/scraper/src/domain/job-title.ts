/** Una card no cumple las reglas de una oferta: se descarta esa card, no la búsqueda. */
export class InvalidJobCardError extends Error {
  readonly code = "INVALID_JOB_CARD";

  constructor(
    readonly field: "title",
    readonly reason: string,
  ) {
    super(`Card inválida (${field}): ${reason}`);
  }
}

export const MAX_TITLE_LENGTH = 300;

/** El mismo texto dos veces, pegado ("AA") o separado por espacio ("A A"). */
const REPEATED = /^(.+?) ?\1$/s;

/**
 * Regla de dominio del título de una oferta: espacios normalizados, no vacío, como mucho {@link MAX_TITLE_LENGTH}
 * caracteres y nunca su propio texto repetido. Los portales a veces duplican el título para lectores de pantalla
 * (un `<span>` visible y otro oculto sin espacio entre ellos); aquí se colapsa a una sola copia.
 *
 * @throws InvalidJobCardError si después de normalizarlo queda vacío o es demasiado largo
 */
export function jobTitle(raw: string): string {
  const text = raw.replace(/\s+/g, " ").trim();
  const title = text.match(REPEATED)?.[1].trim() ?? text;
  if (!title) throw new InvalidJobCardError("title", "está vacío");
  if (title.length > MAX_TITLE_LENGTH) {
    throw new InvalidJobCardError("title", `tiene ${title.length} caracteres (máximo ${MAX_TITLE_LENGTH})`);
  }
  return title;
}
