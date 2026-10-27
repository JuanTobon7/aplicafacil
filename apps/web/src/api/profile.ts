import { api } from "./http";

/**
 * Perfil laboral (profile-service, contracts/openapi/profile-api.yaml). Importar un CV y pedir la búsqueda sugerida
 * devuelven una tarea de IA: se consulta hasta que deja de estar PENDING.
 */

export type SkillCategory = "LANGUAGE" | "FRAMEWORK" | "DATABASE" | "CLOUD" | "TOOL" | "PRACTICE" | "SOFT" | "OTHER";

export type Skill = {
  id: string;
  name: string;
  category: SkillCategory | null;
  level: "BASIC" | "INTERMEDIATE" | "ADVANCED" | "EXPERT" | null;
  yearsOfExperience: number | null;
};

export type Experience = {
  id: string;
  title: string;
  company: string;
  location: string | null;
  startDate: string | null;
  endDate: string | null;
  description: string | null;
  technologies: string[];
};

export type Education = {
  id: string;
  institution: string;
  degree: string | null;
  fieldOfStudy: string | null;
  startDate: string | null;
  endDate: string | null;
};

export type Language = { id: string; name: string; proficiency: string | null };
export type Certification = { id: string; name: string; issuer: string | null; issuedOn: string | null };

export type SearchPreference = {
  keywords: string | null;
  alternatives: string[];
  rationale: string | null;
  remoteOnly: boolean;
  postedWithinDays: number | null;
  location: string | null;
  suggestedAt: string | null;
};

/** contracts/openapi/profile-api.yaml, WorkRole. */
export type WorkRole =
  | "BACKEND" | "FRONTEND" | "FULLSTACK" | "MOBILE" | "DEVOPS" | "DATA_ENGINEERING" | "DATA_SCIENCE" | "DATA_ANALYSIS"
  | "QA" | "UX_UI" | "SECURITY" | "EMBEDDED" | "GAME_DEV" | "PRODUCT" | "IT_SUPPORT" | "OTHER";

export type Seniority = "INTERN" | "JUNIOR" | "MID" | "SENIOR" | "LEAD";

export type WorkProfile = {
  primaryRole: WorkRole;
  secondaryRoles: WorkRole[];
  seniority: Seniority | null;
  title: string | null;
  source: "AI" | "MANUAL";
  updatedAt: string | null;
};

export type WorkProfileDefinition = Pick<WorkProfile, "primaryRole" | "secondaryRoles" | "seniority" | "title">;

export type Profile = {
  headline: string | null;
  summary: string | null;
  skills: Skill[];
  experiences: Experience[];
  education: Education[];
  languages: Language[];
  certifications: Certification[];
  lastSyncedAt: string | null;
  cvImportedAt: string | null;
  searchPreference: SearchPreference;
  /** null = todavía no se sabe (sin CV, sin búsqueda sugerida ni edición). */
  workProfile: WorkProfile | null;
};

export type AiTask = {
  id: string;
  kind: "CV_EXTRACTION" | "SEARCH_SUGGESTION" | "SEARCH_PLAN";
  status: "PENDING" | "COMPLETED" | "FAILED" | "TIMED_OUT";
  fileName: string | null;
  errorCode: string | null;
  errorMessage: string | null;
  createdAt: string;
  completedAt: string | null;
};

export type SearchSettings = {
  keywords: string;
  remoteOnly: boolean;
  postedWithinDays: number | null;
  location: string | null;
};

export const profileApi = {
  me: () => api<Profile>("GET", "/profile/profiles/me"),
  importCv: (file: File, configId: string) => {
    const form = new FormData();
    form.append("file", file);
    form.append("configId", configId);
    return api<AiTask>("POST", "/profile/profiles/me/cv", form);
  },
  suggestSearch: (configId: string) => api<AiTask>("POST", "/profile/profiles/me/search-suggestion", { configId }),
  /** La IA elige las búsquedas del perfil y se lanzan solas en LinkedIn y Computrabajo. */
  planSearches: (configId: string) => api<AiTask>("POST", "/profile/profiles/me/search-plan", { configId }),
  task: (id: string) => api<AiTask>("GET", `/profile/profiles/me/ai-tasks/${id}`),
  configureSearch: (settings: SearchSettings) =>
    api<SearchPreference>("PUT", "/profile/profiles/me/search-preference", settings),
  tasks: () => api<AiTask[]>("GET", "/profile/profiles/me/ai-tasks"),
  /** Definido a mano: la IA ya no lo cambia. */
  defineWorkProfile: (definition: WorkProfileDefinition) =>
    api<WorkProfile>("PUT", "/profile/profiles/me/work-profile", definition),
};

export const isTaskPending = (t: AiTask) => t.status === "PENDING";
