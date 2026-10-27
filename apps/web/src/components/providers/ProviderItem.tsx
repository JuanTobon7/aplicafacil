import { useState } from "react";
import { aiApi, type ProviderConfig, type ProviderStatus } from "../../api/ai";
import { ApiError } from "../../api/http";

const STATUS_LABEL: Record<ProviderStatus, string> = {
  PENDING: "Pendiente",
  INITIALIZING: "Inicializando",
  ACTIVE: "Activo",
  FAILED: "Con error",
  DISABLED: "Desactivado",
};

const API_KEY_PATTERN = /^[\x21-\x7E]{8,512}$/;

type Props = {
  config: ProviderConfig;
  typeName: string;
  busy: boolean;
  run: (id: string, action: () => Promise<unknown>) => Promise<void>;
  onEdit: () => void;
};

/** Una configuración: estado, última prueba y acciones (probar, activar, editar, cambiar la key, eliminar). */
export function ProviderItem({ config: c, typeName, busy, run, onEdit }: Props) {
  const [rotating, setRotating] = useState(false);

  const remove = () => {
    if (!window.confirm(`Se eliminará "${c.displayName}" y su API key. ¿Continuar?`)) return;
    void run(c.id, () => aiApi.remove(c.id));
  };

  return (
    <li aria-busy={busy}>
      <div className="provider-head">
        <strong>{c.displayName}</strong>
        <span className={`badge status-${c.status.toLowerCase()}`}>{STATUS_LABEL[c.status]}</span>
      </div>
      <p className="muted small">
        {typeName} · {c.model}
        {c.baseUrl && <> · {c.baseUrl}</>} · key {c.credentialHint ?? "—"} · v{c.version}
      </p>
      {c.status === "FAILED" && <p className="error small" role="status">{c.statusCode}: {c.statusMessage}</p>}
      <LastTest test={c.lastTest} />
      {rotating ? (
        <RotateKey config={c} busy={busy} run={run} onDone={() => setRotating(false)} />
      ) : (
        <div className="actions">
          <button className="secondary" disabled={!c.enabled || busy} onClick={() => void run(c.id, () => aiApi.test(c.id))}>
            Probar conexión
          </button>
          <button className="secondary" disabled={busy} onClick={() => void run(c.id, () => aiApi.setEnabled(c.id, !c.enabled))}>
            {c.enabled ? "Desactivar" : "Activar"}
          </button>
          <button className="secondary" onClick={onEdit}>Editar</button>
          <button className="secondary" onClick={() => setRotating(true)}>Cambiar API key</button>
          <button className="danger" disabled={busy} onClick={remove}>Eliminar</button>
        </div>
      )}
    </li>
  );
}

function LastTest({ test }: { test: ProviderConfig["lastTest"] }) {
  if (!test) return null;
  return (
    <p className="small" role="status">
      Prueba de conexión: <TestOutcome test={test} />
    </p>
  );
}

function TestOutcome({ test }: { test: NonNullable<ProviderConfig["lastTest"]> }) {
  if (test.success === null) return <>en curso…</>;
  if (test.success) return <span className="ok">correcta ({test.latencyMs ?? "?"} ms)</span>;
  return <span className="error">falló — {test.code}: {test.message}</span>;
}

/** La key nueva solo vive aquí hasta enviarla; la anterior se borra en el backend. */
function RotateKey({ config, busy, run, onDone }: Pick<Props, "busy" | "run"> & { config: ProviderConfig; onDone: () => void }) {
  const [newKey, setNewKey] = useState("");

  const save = () =>
    run(config.id, async () => {
      if (!API_KEY_PATTERN.test(newKey.trim())) throw new ApiError(400, "Pega la API key completa, sin espacios.");
      await aiApi.rotate(config.id, newKey.trim(), config.version);
      onDone();
    });

  return (
    <div className="input-row">
      <input type="password" aria-label="Nueva API key" autoComplete="off" value={newKey} onChange={(e) => setNewKey(e.target.value)} />
      <button onClick={() => void save()} disabled={busy}>Guardar key</button>
      <button className="secondary" onClick={onDone}>Cancelar</button>
    </div>
  );
}
