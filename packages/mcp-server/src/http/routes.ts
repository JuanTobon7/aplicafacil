import { Request, Response } from 'express';
import { OpenRouterAdapter } from '../ai/adapter/impl/open.ai.adapter.js';
import { logger } from '../logger/logger.js';
import { toolsBank } from '../tools/index.js';
import { AiProfileTool } from '../tools/contract/ai.search.profile.js';

/**
 * POST /tools/fill-form
 * Completa campos de formulario usando IA
 */
export async function fillFormRoute(req: Request, res: Response) {
  const requestId = `${Date.now()}-${Math.random()}`;
  logger.info(`[${requestId}] 📝 /tools/fill-form request started`, {
    bodySize: JSON.stringify(req.body).length,
  });

  try {
    const { system, prompt } = req.body;

    if (!system || !prompt) {
      logger.warn(`[${requestId}] Missing required fields`, {
        hasSystem: !!system,
        hasPrompt: !!prompt,
      });
      return res.status(400).json({
        error: 'Missing required fields: system and prompt',
      });
    }

    logger.debug(`[${requestId}] Creating OpenRouter adapter...`);
    const aiProvider = new OpenRouterAdapter();

    logger.debug(
      `[${requestId}] Calling fillForm with prompt size: ${prompt.length}`
    );
    const result = await aiProvider.fillForm({ system, prompt });

    logger.debug(
      `[${requestId}] Received response, size: ${result.length} chars`
    );

    // Validate JSON response
    const parsed = JSON.parse(result);
    logger.success(`[${requestId}] Successfully parsed ${parsed.length} fields`, {
      fields: parsed.map((f: any) => ({
        name: f.fieldName,
        confidence: f.confidence,
        requiresReview: f.requires_review,
      })),
    });

    res.json({
      content: [
        {
          type: 'text',
          text: result,
        },
      ],
    });
  } catch (error) {
    logger.error(`[${requestId}] Error in /tools/fill-form`, error);
    res.status(500).json({
      error: error instanceof Error ? error.message : 'Unknown error',
    });
  }
}

/**
 * POST /tools/embeddings
 * Obtiene embeddings de texto
 */
export async function embeddingsRoute(req: Request, res: Response) {
  const requestId = `${Date.now()}-${Math.random()}`;
  logger.info(`[${requestId}] 🧮 /tools/embeddings request started`);

  try {
    const { data } = req.body;

    if (!data) {
      logger.warn(`[${requestId}] Missing data field`);
      return res.status(400).json({
        error: 'Missing required field: data',
      });
    }

    logger.debug(`[${requestId}] Creating OpenRouter adapter...`);
    const aiProvider = new OpenRouterAdapter();

    logger.debug(`[${requestId}] Requesting embedding for data...`);
    const embedding = await aiProvider.getEmbedding(data);

    logger.success(`[${requestId}] Embedding generated`, {
      dimensions: embedding.length,
      sampleValues: embedding.slice(0, 5),
    });

    res.json({ embedding });
  } catch (error) {
    logger.error(`[${requestId}] Error in /tools/embeddings`, error);
    res.status(500).json({
      error: error instanceof Error ? error.message : 'Unknown error',
    });
  }
}

/**
 * GET /tools/search-profile
 * Busca un perfil por ID
 */
export async function searchProfileRoute(req: Request, res: Response) {
  const requestId = `${Date.now()}-${Math.random()}`;
  const profileId = req.query.id as string;

  logger.info(`[${requestId}] 🔍 /tools/search-profile request`, {
    profileId,
  });

  try {
    if (!profileId) {
      logger.warn(`[${requestId}] Missing profile ID`);
      return res.status(400).json({
        error: 'Missing required query parameter: id',
      });
    }

    logger.debug(`[${requestId}] Getting aiProfileTool from toolsBank...`);
    const aiProfileTool = toolsBank.get<AiProfileTool>('aiProfileTool');

    logger.debug(`[${requestId}] Fetching profile ${profileId}...`);
    const result = await aiProfileTool.getProfileById(profileId);

    logger.success(`[${requestId}] Profile found`, {
      profileId,
      resultType: typeof result,
    });

    res.json(result);
  } catch (error) {
    logger.error(
      `[${requestId}] Error in /tools/search-profile for ID ${profileId}`,
      error
    );
    res.status(500).json({
      error: error instanceof Error ? error.message : 'Unknown error',
    });
  }
}

/**
 * POST /tools/search-people
 * Busca personas según criterios
 */
export async function searchPeopleRoute(req: Request, res: Response) {
  const requestId = `${Date.now()}-${Math.random()}`;
  logger.info(`[${requestId}] 👥 /tools/search-people request started`, {
    criteria: Object.keys(req.body),
  });

  try {
    const criteria = req.body;

    if (!criteria || Object.keys(criteria).length === 0) {
      logger.warn(`[${requestId}] Empty search criteria`);
      return res.status(400).json({
        error: 'Missing search criteria',
      });
    }

    logger.debug(
      `[${requestId}] Search criteria:`,
      JSON.stringify(criteria, null, 2)
    );

    logger.debug(`[${requestId}] Getting aiPeopleTool from toolsBank...`);
    const aiPeopleTool = toolsBank.get<any>('aiPeopleTool');

    logger.debug(`[${requestId}] Executing search...`);
    const result = await aiPeopleTool.searchPeople(criteria);

    logger.success(`[${requestId}] Search completed`, {
      resultCount: Array.isArray(result) ? result.length : 1,
    });

    res.json(result);
  } catch (error) {
    logger.error(`[${requestId}] Error in /tools/search-people`, error);
    res.status(500).json({
      error: error instanceof Error ? error.message : 'Unknown error',
    });
  }
}

/**
 * GET /health
 * Health check endpoint
 */
export function healthRoute(req: Request, res: Response) {
  logger.info('🏥 Health check endpoint called');
  res.json({
    status: 'healthy',
    timestamp: new Date().toISOString(),
    version: '1.0.0',
  });
}

/**
 * GET /
 * Endpoint information
 */
export function infoRoute(req: Request, res: Response) {
  logger.info('📋 Root endpoint called');
  res.json({
    name: 'AplicaFacil MCP Server',
    version: '1.0.0',
    description: 'Model Context Protocol Server for AplicaFacil',
    endpoints: {
      'POST /tools/fill-form': 'Complete job application form fields',
      'POST /tools/embeddings': 'Get text embeddings',
      'GET /tools/search-profile': 'Search profile by ID (query param: id)',
      'POST /tools/search-people': 'Search people by criteria',
      'GET /health': 'Health check',
      'GET /': 'This endpoint',
    },
  });
}
