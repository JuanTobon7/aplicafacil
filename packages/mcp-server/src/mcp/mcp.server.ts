import { z } from 'zod';
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { toolsBank } from '../tools/index.js';
import { AiProfileTool } from '../tools/contract/ai.search.profile.js';
import { logger } from '../logger/logger.js';

/**
 * Initialize and configure the MCP Server
 */
export function initializeMcpServer(): McpServer {
  const mcpServer = new McpServer({
    name: 'aplicafacil-mcp-server',
    version: '1.0.0',
  });

  logger.info('🔧 MCP Server initialized');

  // Register search-profile-by-id tool
  try {
    mcpServer.registerTool(
      'search-profile-by-id',
      {
        description: 'Search for a profile by its ID',
        inputSchema: z.object({
          id: z.string().describe('The profile ID to search for'),
        }),
      },
      async (request: any) => {
        const { id } = request;
        logger.debug('MCP Tool: search-profile-by-id called', { profileId: id });
        try {
          const aiProfileTool = toolsBank.get<AiProfileTool>('aiProfileTool');
          const result = await aiProfileTool.getProfileById(id);
          return {
            content: [
              {
                type: 'text',
                text: typeof result === 'string' ? result : JSON.stringify(result),
              },
            ],
          };
        } catch (error) {
          logger.error('Error in search-profile-by-id tool', error);
          return {
            content: [
              {
                type: 'text',
                text: `Error: ${error instanceof Error ? error.message : 'Unknown error'}`,
              },
            ],
          };
        }
      }
    );
    logger.success('📋 MCP tools registered successfully');
  } catch (error) {
    logger.error('Error registering MCP tools', error);
  }

  return mcpServer;
}
