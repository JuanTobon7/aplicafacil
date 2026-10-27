import { useState, type FormEvent } from "react";
import { Link } from "react-router";
import type { ProviderConfig } from "../../api/ai";
import { ApiError } from "../../api/http";
import { profileApi, type AiTask } from "../../api/profile";

const TASK_LABEL: Record<AiTask["kind"], string> = {
  CV_EXTRACTION: "Leyendo tu CV",
  SEARCH_SUGGESTION: "Eligiendo tu búsqueda de empleo",
  SEARCH_PLAN: "Eligiendo y lanzando tus búsquedas",
};

type Props = {
  providers: ProviderConfig[];
  tasks: AiTask[];
  pending: boolean;
  onStarted: () => Promise<void>;
};

/** Sube el CV; la IA (vía el servidor MCP local) lo descompone en skills, experiencias, estudios… */
export function CvImport({ providers, tasks, pending, onStarted }: Props) {
  const [file, setFile] = useState<File | null>(null);
  const [configId, setConfigId] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const provider = providers.some((p) => p.id === configId) ? configId : providers[0]?.id ?? "";

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!file || !provider) return;
    setSending(true);
    setError(null);
    try {
      await profileApi.importCv(file, provider);
      await onStarted();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "No se pudo enviar el CV.");
    } finally {
      setSending(false);
    }
  };

  return (
    <form className="card" onSubmit={(e) => void submit(e)} aria-busy={sending || pending}>
      <h2>Importar mi CV</h2>
      <p className="muted small">
        PDF o Word, hasta 10 MB. El archivo no se guarda: se lee su texto y se envía al proveedor de IA que elijas.
      </p>
      <CvPicker providers={providers} provider={provider} onFile={setFile} onProvider={setConfigId} />
      <TaskStatus task={tasks[0]} />
      {error && <p className="error" role="alert">{error}</p>}
      <div className="actions">
        <button type="submit" disabled={!file || !provider || sending || pending}>{sending ? "Enviando…" : "Importar"}</button>
      </div>
    </form>
  );
}

type PickerProps = {
  providers: ProviderConfig[];
  provider: string;
  onFile: (file: File | null) => void;
  onProvider: (id: string) => void;
};

function CvPicker({ providers, provider, onFile, onProvider }: PickerProps) {
  if (providers.length === 0) {
    return <p>Necesitas un proveedor de IA activo. <Link to="/ia">Configúralo aquí</Link>.</p>;
  }
  return (
    <div className="grid">
      <input type="file" accept=".pdf,.doc,.docx" aria-label="Archivo del CV" onChange={(e) => onFile(e.target.files?.[0] ?? null)} />
      <select value={provider} aria-label="Proveedor de IA" onChange={(e) => onProvider(e.target.value)}>
        {providers.map((p) => <option key={p.id} value={p.id}>{p.displayName} · {p.model}</option>)}
      </select>
    </div>
  );
}

function TaskStatus({ task }: { task: AiTask | undefined }) {
  if (!task) return null;
  if (task.status === "PENDING") return <p className="muted" role="status">{TASK_LABEL[task.kind]}…</p>;
  if (task.status === "COMPLETED") return null;
  return <p className="error" role="alert">{task.errorMessage ?? "La IA no pudo completar la tarea."}</p>;
}
