import { z } from 'zod';
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { toolsBank } from '../tools/index.js';
import { AiProfileTool } from '../tools/contract/ai.search.profile.js';
import { logger } from '../logger/logger.js';
import { FillFormUseCase, ExtractCvUseCase } from '@aplicafacil/core/application';
import { FillFormRequestDto, FieldDto, JobMetadataDto } from '@aplicafacil/core/domain';

/**
 * Initialize and configure the MCP Server with all tools, resources and prompts.
 *
 * @param fillFormUseCase Use case para analizar formularios de postulación
 * @param extractCvUseCase Use case para extraer perfil de CV
 */
export function initializeMcpServer(
  fillFormUseCase: FillFormUseCase,
  extractCvUseCase: ExtractCvUseCase,
): McpServer {
  const mcpServer = new McpServer({
    name: 'aplicafacil-mcp-server',
    version: '1.0.0',
  });

  logger.info('🔧 MCP Server initialized');

  // ============================================================
  // TOOL: analyze_job_application_form
  // ============================================================
  try {
    mcpServer.registerTool(
      'analyze_job_application_form',
      {
        description: 'Analiza un formulario de postulación y sugiere valores por campo con confianza',
        inputSchema: z.object({
          url: z.string().url(),
          title: z.string(),
          fields: z.array(z.object({
            name: z.string(),
            label: z.string(),
            type: z.string(),
            required: z.boolean(),
            placeholder: z.string().optional(),
            fieldType: z.string().optional(),
            options: z.array(z.object({ value: z.string(), label: z.string() })).optional(),
          })),
          metadata: z.object({
            title: z.string(),
            company: z.string(),
            location: z.string(),
            description: z.string(),
            workplaceType: z.string().optional(),
            employmentType: z.string().optional(),
            postedDate: z.string().optional(),
            skillsMatch: z.object({ matched: z.number(), total: z.number() }).optional(),
            url: z.string().url().optional(),
          }),
        }),
      },
      async (args) => {
        logger.debug('MCP Tool: analyze_job_application_form called', { url: args.url });
        try {
          // Convertir a DTOs del core
          const body: FillFormRequestDto = {
            url: args.url,
            title: args.title,
            fields: args.fields.map((f: FieldDto) => ({
              name: f.name,
              label: f.label,
              type: f.type,
              required: f.required,
              placeholder: f.placeholder,
              fieldType: f.fieldType,
              options: f.options,
            })),
            metadata: {
              title: args.metadata.title,
              company: args.metadata.company,
              location: args.metadata.location,
              description: args.metadata.description,
              workplaceType: args.metadata.workplaceType,
              employmentType: args.metadata.employmentType,
              postedDate: args.metadata.postedDate,
              skillsMatch: args.metadata.skillsMatch,
              url: args.metadata.url,
            },
          };

          const result = await fillFormUseCase.execute(body);
          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify(result),
              },
            ],
          };
        } catch (error) {
          logger.error('Error in analyze_job_application_form tool', error);
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
    logger.success('📋 MCP tool "analyze_job_application_form" registered');
  } catch (error) {
    logger.error('Error registering analyze_job_application_form tool', error);
  }

  // ============================================================
  // TOOL: extract_candidate_profile_from_cv
  // ============================================================
  try {
    mcpServer.registerTool(
      'extract_candidate_profile_from_cv',
      {
        description: 'Extrae un perfil estructurado (skills, experiencias, educación) del texto de un CV',
        inputSchema: z.object({
          data: z.string().min(1, 'CV text is required'),
        }),
      },
      async (args) => {
        logger.debug('MCP Tool: extract_candidate_profile_from_cv called');
        try {
          const result = await extractCvUseCase.execute(args.data);
          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify(result),
              },
            ],
          };
        } catch (error) {
          logger.error('Error in extract_candidate_profile_from_cv tool', error);
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
    logger.success('📋 MCP tool "extract_candidate_profile_from_cv" registered');
  } catch (error) {
    logger.error('Error registering extract_candidate_profile_from_cv tool', error);
  }

  // ============================================================
  // TOOL: search_candidate_profile (antes search-profile-by-id)
  // ============================================================
  try {
    const config = {
      description: 'Busca el perfil vectorial de un candidato por ID',
      inputSchema: z.object({
        id: z.string(),
      }),
    };
    mcpServer.registerTool(
      'search_candidate_profile',
      config as any,
      (async (request: any) => {
        const { id } = request;
        logger.debug('MCP Tool: search_candidate_profile called', { profileId: id });
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
          logger.error('Error in search_candidate_profile tool', error);
          return {
            content: [
              {
                type: 'text',
                text: `Error: ${error instanceof Error ? error.message : 'Unknown error'}`,
              },
            ],
          };
        }
      }) as any
    );
    logger.success('📋 MCP tool "search_candidate_profile" registered');
  } catch (error) {
    logger.error('Error registering search_candidate_profile tool', error);
  }

  // ============================================================
  // TOOL: get_candidate_person
  // ============================================================
  try {
    mcpServer.registerTool(
      'get_candidate_person',
      {
        description: 'Obtiene los datos personales de un candidato (nombre, email, LinkedIn)',
        inputSchema: z.object({
          id: z.string(),
        }),
      },
      async (args) => {
        logger.debug('MCP Tool: get_candidate_person called', { personId: args.id });
        try {
          const aiPeopleTool = toolsBank.get<any>('aiPeopleTool');
          const result = await aiPeopleTool.getPeopleById(args.id);
          return {
            content: [
              {
                type: 'text',
                text: typeof result === 'string' ? result : JSON.stringify(result),
              },
            ],
          };
        } catch (error) {
          logger.error('Error in get_candidate_person tool', error);
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
    logger.success('📋 MCP tool "get_candidate_person" registered');
  } catch (error) {
    logger.error('Error registering get_candidate_person tool', error);
  }

  // ============================================================
  // RESOURCES
  // ============================================================
  try {
    // candidate://profile/{id}
    mcpServer.registerResource(
      'candidate-profile',
      'candidate://profile/{id}',
      {
        title: 'Candidate Profile',
        description: 'Perfil vectorial completo de un candidato (skills, experiencias, educación)',
        mimeType: 'application/json',
      },
      async (uri) => {
        const uriStr = uri.toString();
        const id = uriStr.replace('candidate://profile/', '');
        logger.debug('MCP Resource: candidate://profile/{id}', { id });
        try {
          const aiProfileTool = toolsBank.get<AiProfileTool>('aiProfileTool');
          const result = await aiProfileTool.getProfileById(id);
          return {
            contents: [
              {
                uri: `candidate://profile/${id}`,
                mimeType: 'application/json',
                text: typeof result === 'string' ? result : JSON.stringify(result),
              },
            ],
          };
        } catch (error) {
          logger.error('Error reading candidate profile resource', error);
          return {
            contents: [
              {
                uri: `candidate://profile/${id}`,
                mimeType: 'application/json',
                text: JSON.stringify({ error: error instanceof Error ? error.message : 'Unknown error' }),
              },
            ],
          };
        }
      }
    );

    // candidate://person/{id}
    mcpServer.registerResource(
      'candidate-person',
      'candidate://person/{id}',
      {
        title: 'Candidate Person',
        description: 'Datos personales de un candidato (nombre, email, LinkedIn)',
        mimeType: 'application/json',
      },
      async (uri) => {
        const uriStr = uri.toString();
        const id = uriStr.replace('candidate://person/', '');
        logger.debug('MCP Resource: candidate://person/{id}', { id });
        try {
          const aiPeopleTool = toolsBank.get<any>('aiPeopleTool');
          const result = await aiPeopleTool.getPeopleById(id);
          return {
            contents: [
              {
                uri: `candidate://person/${id}`,
                mimeType: 'application/json',
                text: typeof result === 'string' ? result : JSON.stringify(result),
              },
            ],
          };
        } catch (error) {
          logger.error('Error reading candidate person resource', error);
          return {
            contents: [
              {
                uri: `candidate://person/${id}`,
                mimeType: 'application/json',
                text: JSON.stringify({ error: error instanceof Error ? error.message : 'Unknown error' }),
              },
            ],
          };
        }
      }
    );

    logger.success('📋 MCP resources registered');
  } catch (error) {
    logger.error('Error registering MCP resources', error);
  }

  // ============================================================
  // PROMPTS
  // ============================================================
  try {
    // fill-form-prompt
    mcpServer.registerPrompt(
      'fill-form-prompt',
      {
        title: 'Fill Form Prompt',
        description: 'Plantilla de system prompt + user prompt para completar formularios de postulación',
        argsSchema: {
          url: z.string().url(),
          title: z.string(),
          fields: z.array(z.object({
            name: z.string(),
            label: z.string(),
            type: z.string(),
            required: z.boolean(),
            placeholder: z.string().optional(),
            fieldType: z.string().optional(),
            options: z.array(z.object({ value: z.string(), label: z.string() })).optional(),
          })),
          metadata: z.object({
            title: z.string(),
            company: z.string(),
            location: z.string(),
            description: z.string(),
            workplaceType: z.string().optional(),
            employmentType: z.string().optional(),
            postedDate: z.string().optional(),
            skillsMatch: z.object({ matched: z.number(), total: z.number() }).optional(),
            url: z.string().url().optional(),
          }),
        },
      },
      async (args) => {
        const body: FillFormRequestDto = {
          url: args.url,
          title: args.title,
          fields: args.fields.map((f: FieldDto) => ({
            name: f.name,
            label: f.label,
            type: f.type,
            required: f.required,
            placeholder: f.placeholder,
            fieldType: f.fieldType,
            options: f.options,
          })),
          metadata: {
            title: args.metadata.title,
            company: args.metadata.company,
            location: args.metadata.location,
            description: args.metadata.description,
            workplaceType: args.metadata.workplaceType,
            employmentType: args.metadata.employmentType,
            postedDate: args.metadata.postedDate,
            skillsMatch: args.metadata.skillsMatch,
            url: args.metadata.url,
          },
        };

        // Importar dinámicamente para evitar dependencia circular
        const { FILL_FORM_SYSTEM, buildFillFormPrompt } = await import('@aplicafacil/core/prompts');
        const userPrompt = buildFillFormPrompt(body);

        // MCP prompts solo aceptan role: "user" | "assistant"
        // Combinamos system + user en el primer mensaje
        return {
          messages: [
            {
              role: 'user',
              content: { type: 'text', text: `${FILL_FORM_SYSTEM}\n\n${userPrompt}` },
            },
          ],
        };
      }
    );

    // cv-extract-prompt
    mcpServer.registerPrompt(
      'cv-extract-prompt',
      {
        title: 'CV Extract Prompt',
        description: 'Plantilla de system prompt + user prompt para extraer perfil estructurado de un CV',
        argsSchema: {
          data: z.string().min(1, 'CV text is required'),
        },
      },
      async (args) => {
        const { CV_SYSTEM_EXTRACT } = await import('@aplicafacil/core/prompts');
        return {
          messages: [
            {
              role: 'user',
              content: { type: 'text', text: `You are a system that extracts structured data from CVs.\n\n${CV_SYSTEM_EXTRACT}\n\n${args.data}` },
            },
          ],
        };
      }
    );

    logger.success('📋 MCP prompts registered');
  } catch (error) {
    logger.error('Error registering MCP prompts', error);
  }

  return mcpServer;
}
