import OpenAI from 'openai';
import { AiProvider } from '../../application/ports/ai-provider.js';

/**
 * Configuración del adapter de OmniRoute.
 * Se inyecta desde el bootstrap (env vars), no se lee aquí.
 */
export interface OmniRouteAdapterConfig {
  apiKey?: string;
  baseURL?: string;
  model?: string;
  embeddingModel?: string;
  /** Fetch wrapper opcional para observabilidad (logging de tráfico). */
  fetch?: typeof fetch;
}

/**
 * Adapter de infraestructura: OmniRoute vía SDK de OpenAI.
 *
 * Implementa AiProvider (completación + embeddings). No conoce MCP ni HTTP:
 * es intercambiable por cualquier otro proveedor que implemente AiProvider.
 */
export class OmniRouteAdapter implements AiProvider {
  private readonly client: OpenAI;
  private readonly model: string;
  private readonly embeddingModel: string;

  constructor(config: OmniRouteAdapterConfig) {
    this.client = new OpenAI({
      apiKey: config.apiKey ?? 'not-needed-if-no-auth-configured',
      baseURL: config.baseURL ?? 'http://localhost:20128/v1',
      fetch: config.fetch as any,
    });
    this.model = config.model ?? 'auto';
    this.embeddingModel = config.embeddingModel ?? 'text-embedding-3-small';
  }

  async complete(request: {
    system: string;
    prompt: string;
    data?: string;
  }): Promise<string> {
    const messages: Array<{
      role: 'system' | 'user';
      content: string;
    }> = [
      { role: 'system', content: request.system },
      { role: 'user', content: request.prompt },
    ];

    if (request.data !== undefined) {
      messages.push({ role: 'user', content: request.data });
    }

    const response = await this.client.chat.completions.create({
      model: this.model,
      temperature: 0,
      messages,
    });

    return response.choices[0]?.message?.content ?? '';
  }

  async getEmbedding(data: any): Promise<number[]> {
    const response = await this.client.embeddings.create({
      model: this.embeddingModel,
      input: data,
    });
    return response.data[0].embedding;
  }
}