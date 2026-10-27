import { api } from "./http";

/**
 * Proveedores de IA (ai-config-service, ADR 0005). La API key solo viaja al crear o rotar;
 * el backend nunca la devuelve (solo `credentialHint`, p. ej. "••••abcd").
 */

export type ProviderStatus = "PENDING" | "INITIALIZING" | "ACTIVE" | "FAILED" | "DISABLED";

export type ProviderType = {
  type: string;
  displayName: string;
  allowsBaseUrl: boolean;
  defaultModel: string;
  suggestedModels: string[];
};

export type ModelParameters = {
  temperature: number | null;
  maxTokens: number | null;
  topP: number | null;
};

export type ProviderConfig = {
  id: string;
  providerType: string;
  displayName: string;
  enabled: boolean;
  model: string;
  baseUrl: string | null;
  parameters: ModelParameters;
  credentialHint: string | null;
  version: number;
  status: ProviderStatus;
  statusCode: string | null;
  statusMessage: string | null;
  statusUpdatedAt: string;
  availableModels: string[];
  /** success = null mientras la prueba está en curso */
  lastTest: { at: string; success: boolean | null; latencyMs: number | null; code: string | null; message: string | null } | null;
  createdAt: string;
  updatedAt: string;
};

export type NewProviderConfig = {
  providerType: string;
  displayName: string;
  apiKey: string;
  model: string;
  baseUrl: string | null;
  parameters: ModelParameters;
  enabled: boolean;
};

export type ProviderChanges = {
  displayName: string;
  model: string;
  baseUrl: string | null;
  parameters: ModelParameters;
  expectedVersion: number;
};

export type AiRequest = {
  id: string;
  configId: string;
  status: "PENDING" | "COMPLETED" | "FAILED" | "TIMED_OUT";
  outputText: string | null;
  model: string | null;
  finishReason: string | null;
  inputTokens: number | null;
  outputTokens: number | null;
  errorCode: string | null;
  errorMessage: string | null;
  latencyMs: number | null;
  createdAt: string;
  deadline: string;
  completedAt: string | null;
};

export const aiApi = {
  providerTypes: () => api<ProviderType[]>("GET", "/ai/provider-types"),
  list: () => api<ProviderConfig[]>("GET", "/ai/provider-configs"),
  create: (config: NewProviderConfig) => api<ProviderConfig>("POST", "/ai/provider-configs", config),
  update: (id: string, changes: ProviderChanges) => api<ProviderConfig>("PUT", `/ai/provider-configs/${id}`, changes),
  rotate: (id: string, apiKey: string, expectedVersion: number) =>
    api<ProviderConfig>("PUT", `/ai/provider-configs/${id}/credential`, { apiKey, expectedVersion }),
  setEnabled: (id: string, enabled: boolean) =>
    api<ProviderConfig>("PUT", `/ai/provider-configs/${id}/enabled`, { enabled }),
  remove: (id: string) => api<void>("DELETE", `/ai/provider-configs/${id}`),
  test: (id: string) => api<ProviderConfig>("POST", `/ai/provider-configs/${id}/test`),
  submit: (configId: string, prompt: string, systemPrompt?: string) =>
    api<AiRequest>("POST", "/ai/requests", { configId, prompt, systemPrompt: systemPrompt || null }),
  request: (id: string) => api<AiRequest>("GET", `/ai/requests/${id}`),
};

/** Estados en los que el backend espera algo del mcp-gateway: la página vuelve a consultar. */
export function isSettling(config: ProviderConfig): boolean {
  return config.status === "PENDING" || config.status === "INITIALIZING" || (config.lastTest !== null && config.lastTest.success === null);
}
