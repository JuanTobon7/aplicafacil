import { api } from "./http";
import { subscribe } from "./sse";

export type ConnectionStatus = "DISCONNECTED" | "CONNECTING" | "CONNECTED" | "EXPIRED";

export type ConnectStep =
  | "OPENING_BROWSER"
  | "WAITING_FOR_LOGIN"
  | "READING_PROFILE"
  | "CREATING_ACCOUNT"
  | "SAVING_SESSION";

export type Attempt = {
  attemptId: string;
  state: "CONNECTING" | "CONNECTED" | "FAILED" | "CANCELLED";
  step: ConnectStep | null;
  stepLabel: string | null;
  errorCode: string | null;
  errorMessage: string | null;
  userId: string | null;
  loginCode: string | null;
  outcome: "CREATED" | "UPDATED" | "LINKED" | null;
  profileComplete: boolean;
  switchedAccount: boolean;
};

/** contracts/openapi/linkedin-api.yaml */
export type SessionCheck = { result: "VALID" | "EXPIRED" | "NOT_CONNECTED" | "BUSY" | "UNAVAILABLE" };

export type StatusView = {
  status: ConnectionStatus;
  lastError: string | null;
  connectedAt: string | null;
  lastSyncAt: string | null;
};

export const linkedinApi = {
  browser: () => api<{ state: "INSTALLING" | "READY" | "FAILED" }>("GET", "/linkedin/browser"),
  /** Si hay sesión en la plataforma, el backend lo toma como reconexión de ese usuario. */
  connect: () => api<Attempt>("POST", "/linkedin/connect"),
  attempt: (id: string) => api<Attempt>("GET", `/linkedin/connect/${id}`),
  cancel: (id: string) => api<void>("DELETE", `/linkedin/connect/${id}`),
  status: () => api<StatusView>("GET", "/linkedin/status"),
  /** Al abrir la app: ¿la sesión guardada sigue viva? (la valida headless si no se validó hace poco) */
  checkSession: () => api<SessionCheck>("POST", "/linkedin/session/check"),
  disconnect: () => api<void>("POST", "/linkedin/disconnect"),

  /** SSE del intento con respaldo de polling. Devuelve la función para dejar de escuchar. */
  followAttempt: (id: string, onUpdate: (a: Attempt) => void) =>
    subscribe<Attempt>(`/linkedin/connect/${id}/events`, onUpdate, {
      poll: () => linkedinApi.attempt(id),
      isFinal: (a) => a.state !== "CONNECTING",
    }),

  followStatus: (onUpdate: (s: StatusView) => void) =>
    subscribe<StatusView>("/linkedin/status/events", onUpdate, { poll: linkedinApi.status, intervalMs: 5000 }),
};
