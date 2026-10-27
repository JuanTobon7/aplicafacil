import { api } from "./http";

/** contracts/openapi/computrabajo-api.yaml */
export type ComputrabajoStatus = {
  status: "NOT_CONNECTED" | "CONNECTED" | "EXPIRED";
  candidateName: string | null;
  connectedAt: string | null;
  lastValidatedAt: string | null;
};

export type ComputrabajoStep = "OPENING_BROWSER" | "WAITING_FOR_LOGIN" | "READING_PROFILE" | "SAVING_SESSION";

export type ComputrabajoAttempt = {
  attemptId: string;
  state: "CONNECTING" | "CONNECTED" | "FAILED" | "CANCELLED";
  step: ComputrabajoStep | null;
  stepLabel: string | null;
  errorCode: string | null;
  errorMessage: string | null;
  candidateName: string | null;
};

export type ComputrabajoSessionCheck = { result: "VALID" | "EXPIRED" | "NOT_CONNECTED" | "UNAVAILABLE" };

export const computrabajoApi = {
  status: () => api<ComputrabajoStatus>("GET", "/computrabajo/status"),
  connect: () => api<ComputrabajoAttempt>("POST", "/computrabajo/connect"),
  attempt: (id: string) => api<ComputrabajoAttempt>("GET", `/computrabajo/connect/${id}`),
  cancel: (id: string) => api<void>("DELETE", `/computrabajo/connect/${id}`),
  /** Al abrir la app: ¿la sesión guardada sigue viva? (la valida headless si no se validó hace poco) */
  checkSession: () => api<ComputrabajoSessionCheck>("POST", "/computrabajo/session/check"),
  disconnect: () => api<void>("DELETE", "/computrabajo/connection"),

  /** Polling del intento hasta que termine. Devuelve la función para dejar de seguirlo. */
  followAttempt: (id: string, onUpdate: (a: ComputrabajoAttempt) => void, intervalMs = 1500) => {
    let stopped = false;
    let timer: number | undefined;
    const tick = async () => {
      try {
        const attempt = await computrabajoApi.attempt(id);
        if (stopped) return;
        onUpdate(attempt);
        if (attempt.state !== "CONNECTING") return;
      } catch {
        /* se reintenta en el siguiente ciclo */
      }
      if (!stopped) timer = window.setTimeout(tick, intervalMs);
    };
    void tick();
    return () => {
      stopped = true;
      window.clearTimeout(timer);
    };
  },
};
