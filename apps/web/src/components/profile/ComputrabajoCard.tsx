import type { ComputrabajoAttempt, ComputrabajoStatus, ComputrabajoStep } from "../../api/computrabajo";
import { useComputrabajoConnection, type ConnectError } from "../../hooks/useComputrabajoConnection";

const STEPS: { step: ComputrabajoStep; label: string }[] = [
  { step: "OPENING_BROWSER", label: "Abriendo Computrabajo" },
  { step: "WAITING_FOR_LOGIN", label: "Esperando inicio de sesión" },
  { step: "READING_PROFILE", label: "Leyendo tu cuenta" },
  { step: "SAVING_SESSION", label: "Guardando tu sesión cifrada" },
];

/** Qué hacer ante cada error del backend. */
const HINTS: Record<string, string> = {
  WINDOW_CLOSED: "Vuelve a intentarlo y no cierres la ventana hasta que termine.",
  LOGIN_TIMEOUT: "Tienes 5 minutos para iniciar sesión (también puedes entrar con Google).",
  DATABASE_UNAVAILABLE: "En la carpeta infra/ ejecuta: docker compose up -d",
  BROWSER_NOT_READY: "La primera vez se descarga Chromium; espera un par de minutos.",
  ALREADY_CONNECTING: "Hay otra ventana de inicio de sesión abierta. Termínala o cancélala.",
  SCRAPER_UNAVAILABLE: "Arranca todo con dev-up.ps1.",
  BACKEND_DOWN: "Arranca el backend con dev-up.ps1.",
};

/**
 * Conexión con la cuenta de candidato de Computrabajo: la persona inicia sesión en una ventana (también con Google)
 * y la sesión queda guardada cifrada para que AplicaFácil busque y postule con ella.
 */
export function ComputrabajoCard() {
  const { status, attempt, error, connect, cancel, disconnect } = useComputrabajoConnection();
  const connecting = attempt?.state === "CONNECTING";

  return (
    <section className="card">
      <div className="section-head">
        <h2>Computrabajo</h2>
        <CardAction status={status} connecting={connecting} onConnect={connect} onDisconnect={disconnect} />
      </div>
      {connecting ? <AttemptProgress attempt={attempt} onCancel={cancel} /> : <StatusLine status={status} />}
      {attempt && attempt.state !== "CONNECTING" && attempt.state !== "CONNECTED" && (
        <ErrorLine error={{ code: attempt.errorCode ?? undefined, message: attempt.errorMessage ?? "No se conectó." }} />
      )}
      {error && <ErrorLine error={error} />}
    </section>
  );
}

type ActionProps = {
  status: ComputrabajoStatus | null;
  connecting: boolean;
  onConnect: () => void;
  onDisconnect: () => void;
};

function CardAction({ status, connecting, onConnect, onDisconnect }: ActionProps) {
  if (connecting || !status) return null;
  if (status.status === "CONNECTED") {
    return <button className="secondary" onClick={onDisconnect}>Desconectar</button>;
  }
  return <button onClick={onConnect}>{status.status === "EXPIRED" ? "Iniciar sesión de nuevo" : "Conectar Computrabajo"}</button>;
}

function StatusLine({ status }: { status: ComputrabajoStatus | null }) {
  if (!status) return <p className="muted">Comprobando la sesión…</p>;
  if (status.status === "NOT_CONNECTED") {
    return (
      <p className="muted">
        Conecta tu cuenta de candidato para buscar y postularte también en Computrabajo. Se abre una ventana para que
        inicies sesión tú: AplicaFácil nunca ve tu contraseña.
      </p>
    );
  }
  if (status.status === "EXPIRED") {
    return <p className="warning">Tu sesión de Computrabajo venció. Inicia sesión de nuevo para guardarla.</p>;
  }
  return (
    <p>
      <span className="badge active">Conectado</span>
      {status.candidateName && <> como <strong>{status.candidateName}</strong></>}
      {status.lastValidatedAt && (
        <span className="muted small"> · comprobada {new Date(status.lastValidatedAt).toLocaleString()}</span>
      )}
    </p>
  );
}

function AttemptProgress({ attempt, onCancel }: { attempt: ComputrabajoAttempt | null; onCancel: () => void }) {
  const current = STEPS.findIndex((s) => s.step === attempt?.step);
  return (
    <>
      <p className="muted">Inicia sesión en la ventana que se abrió. No la cierres hasta que termine.</p>
      <ol className="steps">
        {STEPS.map((s, i) => (
          <li key={s.step} className={i < current ? "done" : i === current ? "current" : ""}>{s.label}</li>
        ))}
      </ol>
      <button className="secondary" onClick={onCancel}>Cancelar</button>
    </>
  );
}

function ErrorLine({ error }: { error: ConnectError }) {
  const hint = error.code ? HINTS[error.code] : undefined;
  return (
    <p className="error" role="alert">
      {error.message}
      {hint && <span className="small"> {hint}</span>}
    </p>
  );
}
