/**
 * Puerto de embeddings de la capa de aplicación.
 *
 * Permite que los use cases obtengan vectores sin depender del proveedor
 * concreto (OpenRouter, OmniRoute, un modelo local, etc.).
 */
export interface EmbeddingPort {
  getEmbedding(data: any): Promise<number[]>;
}