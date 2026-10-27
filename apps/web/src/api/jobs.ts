import { api } from "./http";

/** Búsqueda de vacantes (jobs-service, ADR 0006): se encola y un worker la procesa en segundo plano. */

export type JobPlatform = "LINKEDIN" | "COMPUTRABAJO";
export type JobSearchStatus = "QUEUED" | "RUNNING" | "COMPLETED" | "FAILED";

export type JobSearch = {
  id: string;
  platform: JobPlatform;
  keywords: string;
  location: string | null;
  maxResults: number;
  postedWithinDays: number | null;
  remote: boolean;
  status: JobSearchStatus;
  attempts: number;
  foundCount: number | null;
  newCount: number | null;
  errorCode: string | null;
  errorMessage: string | null;
  requestedAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  /** Solo en QUEUED: cuándo la vuelve a intentar el worker. */
  nextAttemptAt: string | null;
  /** La lanzó la IA desde el perfil ("Buscar con IA"); null si la pidió la persona. */
  planId: string | null;
};

export type JobPosting = {
  id: string;
  platform: JobPlatform;
  externalId: string;
  url: string;
  title: string;
  company: string;
  location: string | null;
  postedText: string | null;
  salaryText: string | null;
  quickApply: boolean;
  isNew: boolean;
  position: number;
  firstSeenAt: string;
};

export type NewJobSearch = {
  platform: JobPlatform;
  keywords: string;
  location: string | null;
  maxResults: number;
  postedWithinDays: number | null;
  /** Solo ofertas remotas (en Computrabajo ignora la ciudad). */
  remote: boolean;
};

export const jobsApi = {
  request: (search: NewJobSearch) => api<JobSearch>("POST", "/jobs/searches", search),
  recent: () => api<JobSearch[]>("GET", "/jobs/searches"),
  get: (id: string) => api<JobSearch>("GET", `/jobs/searches/${id}`),
  postings: (id: string) => api<JobPosting[]>("GET", `/jobs/searches/${id}/postings`),
};

export const isPending = (s: JobSearch) => s.status === "QUEUED" || s.status === "RUNNING";
