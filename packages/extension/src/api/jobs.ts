import { api } from "./api";

// ------------------------------------------------------------------
// Contrato con el servidor para la automatización de búsqueda de empleo.
// El servidor expone:
//   GET  /jobs/to-apply        → vacantes listas para auto-aplicar (MATCHED)
//   POST /jobs/:id/status      → actualizar estado tras la aplicación
// ------------------------------------------------------------------
export interface JobToApply {
  id: string;
  title: string;
  company?: string;
  location?: string;
  url?: string;
  matchScore?: number;
  status: string;
}

interface JobsToApplyResponse {
  // El servidor devuelve un array directamente
}

export async function getJobsToApply(): Promise<JobToApply[]> {
  try {
    const response = await api.get<JobToApply[]>("/jobs/to-apply");
    return response.data ?? [];
  } catch (error) {
    console.error("Error fetching jobs to apply:", error);
    return [];
  }
}

export async function updateJobStatus(
  jobId: string,
  status: string,
  detail?: Record<string, any>,
  reason?: string,
): Promise<void> {
  try {
    await api.post(`/jobs/${jobId}/status`, {
      status,
      detail,
      reason,
    });
  } catch (error) {
    console.error(`Error updating job ${jobId} status:`, error);
  }
}
