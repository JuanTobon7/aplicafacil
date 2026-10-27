import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router";
import { authApi } from "../api/auth";
import { ApiError } from "../api/http";
import { linkedinApi, type Attempt, type ConnectStep } from "../api/linkedin";
import { useAuth } from "../auth/AuthContext";

const STEPS: { step: ConnectStep; label: string }[] = [
  { step: "OPENING_BROWSER", label: "Abriendo LinkedIn" },
  { step: "WAITING_FOR_LOGIN", label: "Esperando inicio de sesión" },
  { step: "READING_PROFILE", label: "Leyendo tu perfil" },
  { step: "CREATING_ACCOUNT", label: "Creando tu cuenta" },
  { step: "SAVING_SESSION", label: "Guardando tu sesión cifrada" },
];

/** Qué hacer ante cada error del backend. */
const HINTS: Record<string, string> = {
  WINDOW_CLOSED: "Vuelve a intentarlo y no cierres la ventana hasta que termine.",
  LOGIN_TIMEOUT: "Tienes 5 minutos para iniciar sesión (incluye la verificación en dos pasos).",
  DATABASE_UNAVAILABLE: "En la carpeta infra/ ejecuta: docker compose up -d",
  BROWSER_NOT_READY: "La primera vez se descarga Chromium; espera un par de minutos.",
  ALREADY_CONNECTING: "Hay otra ventana de LinkedIn abierta. Termina ese inicio de sesión o cancélalo.",
  IDENTITY_SERVICE_UNAVAILABLE: "Verifica que identity-service esté corriendo.",
  BACKEND_DOWN: "Arranca el backend con dev-up.ps1.",
};

type Props = { onCancel: () => void };

/**
 * Abre el navegador visible (POST /linkedin/connect) y sigue el intento por SSE (polling de respaldo).
 * Al terminar canjea el código de un solo uso por la sesión de la plataforma.
 */
export function ConnectionProgress({ onCancel }: Props) {
  const auth = useAuth();
  const navigate = useNavigate();
  const [attempt, setAttempt] = useState<Attempt | null>(null);
  const [error, setError] = useState<{ code?: string; message: string } | null>(null);
  const started = useRef(false);

  useEffect(() => {
    // StrictMode monta dos veces en desarrollo: un solo intento
    if (started.current) return;
    started.current = true;
    let stop: (() => void) | undefined;
    let exchanged = false;

    linkedinApi
      .connect()
      .then((initial) => {
        setAttempt(initial);
        stop = linkedinApi.followAttempt(initial.attemptId, async (update) => {
          setAttempt(update);
          if (update.state === "CONNECTED" && update.loginCode && !exchanged) {
            exchanged = true;
            try {
              auth.start(await authApi.exchange(update.attemptId, update.loginCode));
              navigate("/perfil", { replace: true, state: { welcome: update.outcome, switched: update.switchedAccount } });
            } catch (e) {
              setError(toError(e));
            }
          } else if (update.state === "FAILED" || update.state === "CANCELLED") {
            setError({ code: update.errorCode ?? undefined, message: update.errorMessage ?? "No se pudo conectar." });
          }
        });
      })
      .catch((e) => setError(toError(e)));

    return () => stop?.();
  }, [auth, navigate]);

  const cancel = async () => {
    if (attempt?.state === "CONNECTING") await linkedinApi.cancel(attempt.attemptId).catch(() => undefined);
    onCancel();
  };

  if (error) {
    return (
      <div role="alert">
        <p className="error">{error.message}</p>
        {error.code && HINTS[error.code] && <p className="muted small">{HINTS[error.code]}</p>}
        <div className="actions">
          <button className="secondary" onClick={onCancel}>Volver</button>
        </div>
      </div>
    );
  }

  const current = STEPS.findIndex((s) => s.step === attempt?.step);
  return (
    <div aria-live="polite">
      <p>Se abrió una ventana con la página oficial de LinkedIn. Inicia sesión allí; aquí verás el avance.</p>
      <ol className="steps">
        {STEPS.map((s, i) => (
          <li key={s.step} className={i < current ? "done" : i === current ? "current" : ""}>
            {s.label}
            {i === current && "…"}
          </li>
        ))}
      </ol>
      <div className="actions">
        <button className="secondary" onClick={() => void cancel()} disabled={!attempt || attempt.state !== "CONNECTING"}>
          Cancelar
        </button>
      </div>
    </div>
  );
}

function toError(e: unknown): { code?: string; message: string } {
  if (e instanceof ApiError) return { code: e.code, message: e.message };
  return { message: "Ocurrió un error inesperado." };
}
