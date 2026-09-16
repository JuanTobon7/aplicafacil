/**
 * Tokens de inyección de dependencias para los ports de la capa de aplicación.
 *
 * Son strings (no dependen de NestJS ni de ningún framework): cualquier
 * contenedor de DI puede usarlos como identificadores.
 */
export const AI_COMPLETION_PORT = 'AI_COMPLETION_PORT';
export const CACHE_PORT = 'CACHE_PORT';
export const LOGGER_PORT = 'LOGGER_PORT';