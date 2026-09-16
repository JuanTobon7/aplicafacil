import { AiProvider } from '../../application/ports/ai-provider.js';
import { OpenRouterAdapter } from './open-router.adapter.js';
import { OmniRouteAdapter } from './omni-route.adapter.js';

/**
 * Configuración de la fábrica de proveedores de IA.
 * Se construye en el bootstrap con los adapters ya instanciados.
 */
export interface AiProviderFactoryConfig {
  provider: string;
  openRouter: OpenRouterAdapter;
  omniRoute: OmniRouteAdapter;
}

/**
 * Fábrica de proveedores de IA.
 *
 * Es la ÚNICA vía para obtener un AiProvider: el transporte (MCP o HTTP)
 * nunca instancia adapters directamente. Cambiar de proveedor = cambiar
 * la config (env LLM_PROVIDER), sin tocar tools ni rutas.
 */
export class AiProviderFactory {
  constructor(private readonly config: AiProviderFactoryConfig) {}

  getProvider(): AiProvider {
    switch (this.config.provider.toLowerCase()) {
      case 'openrouter':
        return this.config.openRouter;
      case 'omniroute':
        return this.config.omniRoute;
      default:
        throw new Error(
          `AiProvider "${this.config.provider}" no soportado`,
        );
    }
  }
}