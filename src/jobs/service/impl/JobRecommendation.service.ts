import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { createHash } from 'node:crypto';
import { FillFormRequestDto } from '../../dto/req/FillFormRequestDto';
import { FieldDto } from '../../dto/helpers/FieldDto';
import { JobRecommendationService } from '../contract/JobRecommendaion.service';
import { McpClientService } from '../../../mcp-client/mcp-client.service';
import { FILL_FORM_SYSTEM, buildFillFormPrompt } from '../../../mcp-client/prompts/fill-form.prompt';
import { RedisService } from '../../../common/redis/redis.service';

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

// Prefijo de llaves en Redis: e-tag por input, por "solicitud abierta" (URL)
const REDIS_KEY_PREFIX = 'autoapply:etag';

@Injectable()
export class JobRecommendationServiceImpl implements JobRecommendationService {
  private readonly logger = new Logger(JobRecommendationServiceImpl.name);

  constructor(
    private readonly mcpClient: McpClientService,
    private readonly redis: RedisService,
    private readonly configService: ConfigService,
  ) {}

  async fillFormFields(body: FillFormRequestDto): Promise<FillFormResponse> {
    if (!body.fields?.length) {
      return { status: 'ok', fields: [], warnings: ['No se recibieron campos'] };
    }

    const ttl = Number(this.configService.get<string>('REDIS_ETAG_TTL')) || 3600;

    try {
      // 1) E-tag por cada input: firma estable del campo (label, name, type, options…)
      const keys = body.fields.map((field) => this.keyFor(body.url, field));

      // 2) Revisa caché en Redis: los campos con e-tag idéntico se reutilizan
      //    y NO se vuelven a analizar con el LLM (evita sobre-análisis en
      //    re-escaneos del mismo formulario, p.ej. por scroll o cambios de paso).
      const cachedRaw = await this.redis.mget(keys);
      const cachedByField = new Map<string, FieldResult>();
      const missingFields: FieldDto[] = [];
      const missingKeys: string[] = [];

      body.fields.forEach((field, index) => {
        const raw = cachedRaw?.[index];
        if (raw) {
          try {
            cachedByField.set(field.name, JSON.parse(raw) as FieldResult);
            return;
          } catch {
            this.logger.warn(`E-tag corrupto para "${field.name}", se re-analiza`);
          }
        }
        missingFields.push(field);
        missingKeys.push(keys[index]);
      });

      // 3) Solo si hay campos nuevos/cambiados se invoca al LLM
      if (missingFields.length > 0) {
        const results = await this.analyzeWithLlm({ ...body, fields: missingFields });

        // 4) Guarda en Redis el e-tag de cada input analizado (TTL = sesión abierta)
        for (const result of results) {
          const index = missingFields.findIndex((f) => f.name === result.fieldName);
          if (index >= 0) {
            cachedByField.set(result.fieldName, result);
            await this.redis.set(missingKeys[index], JSON.stringify(result), ttl);
          }
        }
      } else {
        this.logger.log(
          `Todos los campos (${body.fields.length}) estaban en caché. LLM omitido.`,
        );
      }

      // 5) Ensambla la respuesta en el orden original del formulario
      const results: FieldResult[] = body.fields
        .map((field) => cachedByField.get(field.name))
        .filter((r): r is FieldResult => !!r);

      const returnedNames = new Set(results.map((r) => r.fieldName));
      const missing = body.fields
        .filter((f) => !returnedNames.has(f.name))
        .map((f) => `Campo "${f.label}" no fue procesado`);

      if (missing.length > 0) {
        this.logger.warn(`Missing fields: ${missing.join(', ')}`);
      }

      let status: FillFormResponse['status'] = 'ok';
      if (results.length === 0) status = 'error';
      else if (missing.length > 0) status = 'partial';

      return {
        status,
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

  private async analyzeWithLlm(body: FillFormRequestDto): Promise<FieldResult[]> {
    const userPrompt = buildFillFormPrompt(body);

    this.logger.debug(
      `Requesting form fill from MCP server (${body.fields.length} campos sin caché)...`,
    );
    const rawResponse = await this.mcpClient.fillForm({
      system: FILL_FORM_SYSTEM,
      prompt: userPrompt,
    });

    const results: FieldResult[] = JSON.parse(rawResponse);

    // Verifica que el LLM haya devuelto un resultado por cada campo pedido
    const returnedNames = new Set(results.map((r) => r.fieldName));
    const missing = body.fields.filter((f) => !returnedNames.has(f.name));
    if (missing.length > 0) {
      const labels = missing.map((f) => `"${f.label}"`).join(', ');
      this.logger.warn(`Missing fields del LLM: ${labels}`);
    }

    return results;
  }

  // ------------------------------------------------------------------
  // E-tag por input: hash de la firma del campo + URL de la solicitud abierta
  // ------------------------------------------------------------------
  private keyFor(url: string, field: FieldDto): string {
    const urlHash = createHash('sha256').update(url).digest('hex').slice(0, 16);
    const fieldHash = createHash('sha256')
      .update(
        JSON.stringify({
          label: field.label,
          name: field.name,
          type: field.type,
          required: field.required,
          placeholder: field.placeholder,
          fieldType: field.fieldType,
          options: field.options,
        }),
      )
      .digest('hex')
      .slice(0, 16);

    return `${REDIS_KEY_PREFIX}:${urlHash}:${fieldHash}`;
  }
}