import { JobForm } from "../core/types/forms";
import { api } from "./api";

// ------------------------------------------------------------------
// Contrato con el servidor: POST /api/v1/recommendations
// El servidor responde con los campos sugeridos por el LLM, donde
// fieldName coincide con el atributo `name` del input en el DOM.
// ------------------------------------------------------------------
export interface Recommendation {
  fieldName: string;        // coincide con field.name del DOM
  value: string | null;     // valor sugerido (null = sin recomendación)
  confidence?: number;      // confianza del LLM (0-1)
  requires_review?: boolean; // true si el LLM no estaba seguro
}

interface RecommendationsResponse {
  message: string;
  recommendations: Recommendation[];
}

export async function postRecommendations(
    payload: JobForm
): Promise<Recommendation[]> {
  try {
    console.log("Posting form data to API for recommendations:", payload);
    const response = await api.post<RecommendationsResponse>("/recommendations", payload);
    const recommendations = response.data.recommendations ?? [];
    console.log("Received recommendations from API:", recommendations);
    return recommendations;
  } catch (error) {
    console.error("Error fetching recommendations:", error);
    return [];
  }
}