import { AiCompletionPort } from './ai-completion.port.js';
import { EmbeddingPort } from './embedding.port.js';

/**
 * Proveedor de IA combinado: completación + embeddings.
 *
 * Es el contrato que exponen los adapters de infraestructura
 * (OpenRouter, OmniRoute, etc.) y el que devuelve AiProviderFactory.
 */
export interface AiProvider extends AiCompletionPort, EmbeddingPort {}