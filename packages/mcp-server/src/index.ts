import { initializeMcpServer } from './mcp/mcp.server.js';
import { createExpressApp } from './server/app.js';
import { logger } from './logger/logger.js';

/**
 * Main entry point for the MCP Server
 */
async function main() {
  try {
    logger.info('🚀 Starting AplicaFacil MCP Server...');

    // Initialize MCP Server
    const mcpServer = initializeMcpServer();
    logger.success('✅ MCP Server initialized');

    // Create Express app with routes
    const app = createExpressApp();
    logger.success('✅ Express app configured with routes');

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
        logger.info('');
        logger.info(`🌐 MCP Server ready at http://localhost:${port}`);
        logger.info(`📡 Environment: ${process.env.NODE_ENV || 'development'}`);
        logger.debug('Configuration:', {
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
