/**
 * Puerto de completación de IA.
 *
 * Es la frontera entre la capa de aplicación y los proveedores de LLM.
 * La implementación concreta (OpenRouter, OmniRoute, un LLM local, etc.)
 * vive en infraestructura y se inyecta aquí.
 */
export interface AiCompletionPort {
  /**
   * Envía un system prompt + user prompt al LLM y devuelve la respuesta cruda.
   *
   * `data` es opcional: cuando está presente, se envía como un mensaje
   * adicional de usuario (p.ej. el texto crudo de un CV).
   */
  complete(request: {
    system: string;
    prompt: string;
    data?: string;
  }): Promise<string>;
}