import { Injectable } from '@nestjs/common';
import { AiProviderFactory } from 'src/ai';
import { FillFormRequestDto } from '../../dto/req/FillFormRequestDto';
import { buildFillFormPrompt, FILL_FORM_SYSTEM } from 'src/ai/config/propmts/fill-form.prompt';
import { JobRecommendationService } from '../contract/JobRecommendaion.service';
 
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
 
  constructor(private readonly factory: AiProviderFactory) {}
 
  async fillFormFields(body: FillFormRequestDto): Promise<FillFormResponse> {
    if (!body.fields?.length) {
      return { status: 'ok', fields: [], warnings: ['No se recibieron campos'] };
    }
 
    const userPrompt = buildFillFormPrompt(body);
    const llm = this.factory.getProvider();
    const rawResponse     = await llm.fillForm({ system: FILL_FORM_SYSTEM, prompt: userPrompt });
    const results: FieldResult[] = JSON.parse(rawResponse);
    // Verifica que el LLM haya devuelto un resultado por cada campo
    const returnedNames = new Set(results.map(r => r.fieldName));
    const missing = body.fields
      .filter(f => !returnedNames.has(f.name))
      .map(f => `Campo "${f.label}" no fue procesado`);
    console.log("Missing fields:", missing);
    return {
      status:   results.length === 0 ? 'error' : missing.length > 0 ? 'partial' : 'ok',
      fields:   results,
      warnings: missing,
    };
  }
}