import { Injectable, Logger } from '@nestjs/common';
import { FillFormRequestDto } from '../../dto/req/FillFormRequestDto';
import { JobRecommendationService } from '../contract/JobRecommendaion.service';
import { McpClientService } from '../../../mcp-client/mcp-client.service';
import { FILL_FORM_SYSTEM, buildFillFormPrompt } from '../../../mcp-client/prompts/fill-form.prompt';

export interface FieldResult {
  fieldName:       string;    // coincide con field.name del DOM
  value:           string | null;
  confidence:      number;
  requires_review: boolean;
}

export interface FillFormResponse {
  status:   'ok' | 'partial' | 'error';
  fields:   FieldResult[];
  warnings: string[];
}

@Injectable()
export class JobRecommendationServiceImpl implements JobRecommendationService {
  private readonly logger = new Logger(JobRecommendationServiceImpl.name);

  constructor(private readonly mcpClient: McpClientService) {}

  async fillFormFields(body: FillFormRequestDto): Promise<FillFormResponse> {
    if (!body.fields?.length) {
      return { status: 'ok', fields: [], warnings: ['No se recibieron campos'] };
    }

    try {
      const userPrompt = buildFillFormPrompt(body);

      this.logger.debug('Requesting form fill from MCP server...');
      const rawResponse = await this.mcpClient.fillForm({
        system: FILL_FORM_SYSTEM,
        prompt: userPrompt,
      });

      const results: FieldResult[] = JSON.parse(rawResponse);

      // Verifica que el LLM haya devuelto un resultado por cada campo
      const returnedNames = new Set(results.map(r => r.fieldName));
      const missing = body.fields
        .filter(f => !returnedNames.has(f.name))
        .map(f => `Campo "${f.label}" no fue procesado`);

      if (missing.length > 0) {
        this.logger.warn(`Missing fields: ${missing.join(', ')}`);
      }

      return {
        status: results.length === 0 ? 'error' : missing.length > 0 ? 'partial' : 'ok',
        fields: results,
        warnings: missing,
      };
    } catch (error) {
      this.logger.error(
        `Error filling form fields: ${error instanceof Error ? error.message : String(error)}`,
      );
      throw error;
    }
  }
}