export interface FieldResult {
  fieldName: string; // coincide con field.name del DOM
  value: string | null;
  confidence: number;
  requires_review: boolean;
}

export interface FillFormResponse {
  status: 'ok' | 'partial' | 'error';
  fields: FieldResult[];
  warnings: string[];
}