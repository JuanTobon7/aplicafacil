/**
 * Una página guardada para estudiar su DOM (selectores, detector de sesión, lector de perfil). Nunca sale del equipo:
 * se guarda en una carpeta ignorada por git y ya saneada.
 */
export type DomSnapshot = {
  portal: string;
  /** Paso del recorrido ("02-resultados-busqueda"). */
  step: string;
  url: string;
  title: string;
  /** Nombres (nunca valores) de las cookies del portal: sirven para elegir la cookie que indica sesión. */
  cookieNames: string[];
  html: string;
};

/** Bloques que no aportan estructura y suelen traer datos de la persona o tokens (LinkedIn mete JSON en `<code>`). */
const DROPPED_BLOCKS = ["script", "style", "noscript", "code", "template", "svg"];
const EMAIL = /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g;
/** Identificadores del miembro en LinkedIn; los de ofertas (urn:li:jobPosting) se conservan. */
const MEMBER_URN = /urn:li:(?:fsd_profile|fs_miniProfile|member|fsd_miniProfile):[A-Za-z0-9_-]+/g;
/** Valores de formularios, tokens y fuentes de imágenes (fotos de perfil). */
const SENSITIVE_ATTRIBUTE = /\s(value|src|srcset|data-delayed-url|data-ghost-url|[\w-]*token[\w-]*|[\w-]*csrf[\w-]*)="[^"]*"/gi;

/**
 * Quita del HTML lo que identifica a la persona o da acceso a su cuenta y deja la estructura (etiquetas, clases,
 * atributos `data-*`, textos de la página) que necesitan los selectores.
 *
 * @param redact textos literales a ocultar además de los correos (nombre de la persona, por ejemplo)
 */
export function sanitizeHtml(html: string, redact: readonly string[] = []): string {
  let out = DROPPED_BLOCKS.reduce(
    (acc, tag) => acc.replace(new RegExp(`<${tag}\\b[^>]*>[\\s\\S]*?</${tag}>`, "gi"), `<${tag}></${tag}>`),
    html,
  );
  out = out.replace(SENSITIVE_ATTRIBUTE, (_m, name: string) => ` ${name}="[oculto]"`);
  out = out.replace(EMAIL, "[correo]").replace(MEMBER_URN, "urn:li:[miembro]");
  for (const text of redact.map((t) => t.trim()).filter((t) => t.length >= 3)) {
    out = out.replace(new RegExp(escapeRegExp(text), "gi"), "[oculto]");
  }
  return out;
}

function escapeRegExp(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
