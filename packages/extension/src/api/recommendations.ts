import { JobForm } from "../core/types/forms";
import { api } from "./api";

export interface Recommendation {
  id: string;
  title: string;
  description: string;
  url: string;
}

export async function postRecommendations(
    payload: JobForm
): Promise<Recommendation[]> {
  try {
    console.log("Posting form data to API for recommendations:", payload);
    const response = await api.post<Recommendation[]>("/recommendations", payload);
    console.log("Received recommendations from API:", response);
    return response.data;
  } catch (error) {
    console.error("Error fetching recommendations:", error);
    return [];
  }
}