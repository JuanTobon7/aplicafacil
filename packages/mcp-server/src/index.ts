import { initializeMcpServer } from './mcp/mcp.server.js';
import { createExpressApp } from './server/app.js';
import { logger } from './logger/logger.js';
import { httpInterceptor } from './http/http-interceptor.js';
import {
  AiProviderFactory,
  OpenRouterAdapter,
  OmniRouteAdapter,
} from '@aplicafacil/core/infrastructure';
import {
  FillFormUseCase,
  ExtractCvUseCase,
} from '@aplicafacil/core/application';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { StreamableHTTPServerTransport } from '@modelcontextprotocol/sdk/server/streamableHttp.js';

import 'dotenv/config';

/**
 * Composition root del MCP Server.
 *
 * Aquí se construyen las dependencias de infraestructura (adapters de IA,
 * fábrica de proveedores, use cases) y se inyectan en los transportes
 * (MCP stdio/streamable-http y HTTP Express).
 * Ni las rutas HTTP ni las MCP Tools instancian proveedores directamente.
 */
function buildAiProviderFactory(): AiProviderFactory {
  const openRouter = new OpenRouterAdapter({
    apiKey: process.env.OPENROUTER_API_KEY ?? '',
    baseURL: process.env.OPENROUTER_BASE_URL ?? 'https://openrouter.ai/api/v1',
    model: process.env.OPENROUTER_MODEL,
    embeddingModel: process.env.OPENROUTER_EMBEDDING_MODEL,
    fetch: httpInterceptor.createFetchWrapper('OpenRouter') as typeof fetch,
  });

  const omniRoute = new OmniRouteAdapter({
    apiKey: process.env.OMNIROUTE_API_KEY,
    baseURL: process.env.OMNIROUTE_BASE_URL,
    model: process.env.OMNIROUTE_MODEL,
    embeddingModel: process.env.OMNIROUTE_EMBEDDING_MODEL,
    fetch: httpInterceptor.createFetchWrapper('OmniRoute') as typeof fetch,
  });

  return new AiProviderFactory({
    provider: process.env.LLM_PROVIDER ?? 'openrouter',
    openRouter,
    omniRoute,
  });
}

/**
 * Main entry point for the MCP Server
 */
async function main() {
  try {
    logger.info('🚀 Starting AplicaFacil MCP Server...');

    // Composition root: construye la fábrica de proveedores de IA
    const aiProviderFactory = buildAiProviderFactory();
    logger.success('✅ AI provider factory initialized');

    // Use cases del core (transport-agnostic) - compartidos entre transports
    const fillFormUseCase = new FillFormUseCase(
      aiProviderFactory.getProvider(),
      // CachePort y LoggerPort se inyectan desde el server NestJS vía DI.
      // Aquí usamos implementaciones stub para el transporte MCP standalone.
      // En producción, el server NestJS provee RedisService + NestLoggerAdapter.
      {
        mget: async () => null,
        set: async () => {},
      },
      {
        debug: (msg: string, ...args: unknown[]) => logger.debug(msg, ...args),
        info: (msg: string, ...args: unknown[]) => logger.info(msg, ...args),
        warn: (msg: string, ...args: unknown[]) => logger.warn(msg, ...args),
        error: (msg: string, ...args: unknown[]) => logger.error(msg, ...args),
      },
      Number(process.env.REDIS_ETAG_TTL) || 3600,
    );

    const extractCvUseCase = new ExtractCvUseCase(
      aiProviderFactory.getProvider(),
      {
        debug: (msg: string, ...args: unknown[]) => logger.debug(msg, ...args),
        info: (msg: string, ...args: unknown[]) => logger.info(msg, ...args),
        warn: (msg: string, ...args: unknown[]) => logger.warn(msg, ...args),
        error: (msg: string, ...args: unknown[]) => logger.error(msg, ...args),
      },
    );

    // ============================================================
    // MCP TRANSPORT 1: stdio (para clientes locales: Claude Desktop, VS Code)
    // ============================================================
    const stdioMcpServer = initializeMcpServer(fillFormUseCase, extractCvUseCase);
    const stdioTransport = new StdioServerTransport();
    await stdioMcpServer.connect(stdioTransport);
    logger.success('✅ MCP stdio transport connected');

    // ============================================================
    // MCP TRANSPORT 2: streamable-http (para clientes remotos)
    // ============================================================
    const httpMcpServer = initializeMcpServer(fillFormUseCase, extractCvUseCase);
    const streamableHttpTransport = new StreamableHTTPServerTransport({
      sessionIdGenerator: () => crypto.randomUUID(),
    });
    await httpMcpServer.connect(streamableHttpTransport);
    logger.success('✅ MCP streamable-http transport connected');

    // Create Express app with routes (recibe la fábrica por inyección)
    const app = createExpressApp(aiProviderFactory);
    logger.success('✅ Express app configured with routes');

    // Montar el transport streamable-http en Express
    app.post('/mcp', async (req, res) => {
      await streamableHttpTransport.handleRequest(req, res, req.body);
    });
    app.get('/mcp', async (req, res) => {
      await streamableHttpTransport.handleRequest(req, res);
    });
    app.delete('/mcp', async (req, res) => {
      await streamableHttpTransport.handleRequest(req, res);
    });

    // Start server and wait for it to be ready
    const port = parseInt(process.env.PORT || '3001', 10);
    await new Promise<void>((resolve) => {
      app.listen(port, () => {
        logger.success(`🚀 HTTP Server listening on port ${port}`);
        logger.info('');
        logger.info('📍 Available Endpoints:');
        logger.info('   POST   /tools/fill-form          - Complete form fields with AI');
        logger.info('   POST   /tools/embeddings        - Get text embeddings');
        logger.info('   GET    /tools/search-profile    - Search profile by ID');
        logger.info('   POST   /tools/search-people     - Search people by criteria');
        logger.info('   GET    /health                  - Health check');
        logger.info('   GET    /                        - API info');
        logger.info('   POST   /mcp                     - MCP streamable-http endpoint');
        logger.info('   GET    /mcp                     - MCP streamable-http endpoint');
        logger.info('   DELETE /mcp                     - MCP streamable-http endpoint');
        logger.info('   stdio                           - MCP stdio transport (this process)');
        logger.info('');
        logger.info(`🌐 MCP Server ready at http://localhost:${port}`);
        logger.info(`📡 Environment: ${process.env.NODE_ENV || 'development'}`);
        logger.debug('Configuration:', {
          llmProvider: process.env.LLM_PROVIDER ?? 'openrouter',
          hasOpenRouterKey: !!process.env.OPENROUTER_API_KEY,
          hasOpenRouterUrl: !!process.env.OPENROUTER_BASE_URL,
          hasBackendUrl: !!process.env.BACKEND_URL,
        });
        logger.info('');
        logger.success('✅ MCP Server fully initialized and ready to receive requests');
        resolve();
      });
    });
  } catch (error) {
    logger.error('❌ Failed to start server', error);
    process.exit(1);
  }
}

main().catch((error) => {
  logger.error('Unhandled error in main', error);
  process.exit(1);
});
