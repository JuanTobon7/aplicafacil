/** Criterios de una búsqueda de empleo, iguales para todos los portales. */
export type JobSearchCriteria = {
  keywords: string;
  location?: string;
  /** Tope de ofertas a devolver (1..200). */
  maxResults: number;
  /** Solo ofertas publicadas en los últimos N días (si el portal lo permite). */
  postedWithinDays?: number;
  /** Solo ofertas remotas (si el portal lo permite). */
  remote?: boolean;
};

/** Una oferta tal como se ve en la card del listado, normalizada. Sin descripción completa. */
export type JobCard = {
  /** Id de la oferta en el portal: con él se deduplica. */
  externalId: string;
  url: string;
  title: string;
  company: string;
  location: string | null;
  /** Texto del portal ("hace 2 días", "2026-10-14"…); no todos dan una fecha exacta. */
  postedText: string | null;
  salaryText: string | null;
  /** Postulación rápida dentro del portal (Easy Apply en LinkedIn). */
  quickApply: boolean;
};

export const MAX_SEARCH_RESULTS = 200;
