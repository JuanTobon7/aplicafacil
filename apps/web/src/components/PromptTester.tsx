import { useEffect, useRef, useState, type FormEvent } from "react";
import { aiApi, type AiRequest, type ProviderConfig } from "../api/ai";
import { ApiError } from "../api/http";

const POLL_MS = 1000;

/** Envía un prompt y consulta la solicitud hasta un estado final (el backend responde 202). */
function usePromptRequest() {
  const [request, setRequest] = useState<AiRequest | null>(null);
  const [error, setError] = useState<string | null>(null);
  const timer = useRef<number | undefined>(undefined);
  useEffect(() => () => window.clearTimeout(timer.current), []);

  const poll = (id: string) => {
    timer.current = window.setTimeout(async () => {
      try {
        const current = await aiApi.request(id);
        setRequest(current);
        if (current.status === "PENDING") poll(id);
      } catch {
        poll(id);
      }
    }, POLL_MS);
  };

  const submit = async (configId: string, prompt: string) => {
    setError(null);
    if (!prompt.trim()) {
      setError("Escribe algo para enviar.");
      return;
    }
    try {
      const created = await aiApi.submit(configId, prompt.trim());
      setRequest(created);
      poll(created.id);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "No se pudo enviar.");
    }
  };

  return { request, error, submit, pending: request?.status === "PENDING" };
}

/**
 * Prueba un prompt de punta a punta: backend → RabbitMQ → mcp-gateway → proveedor → resultado.
 */
export function PromptTester({ configs }: { configs: ProviderConfig[] }) {
  const usable = configs.filter((c) => c.enabled);
  const [configId, setConfigId] = useState("");
  const [prompt, setPrompt] = useState("");
  const { request, error, submit, pending } = usePromptRequest();
  const selected = usable.some((c) => c.id === configId) ? configId : usable[0]?.id ?? "";

  if (!usable.length) return null;

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    void submit(selected, prompt);
  };

  return (
    <form className="card" onSubmit={onSubmit}>
      <h2>Probar un prompt</h2>
      <div className="grid">
        <label>
          Configuración
          <select value={selected} onChange={(e) => setConfigId(e.target.value)}>
            {usable.map((c) => <option key={c.id} value={c.id}>{c.displayName} · {c.model}</option>)}
          </select>
        </label>
      </div>
      <label>
        Prompt
        <textarea rows={3} maxLength={32000} value={prompt} onChange={(e) => setPrompt(e.target.value)} />
      </label>
      {error && <p className="error" role="alert">{error}</p>}
      <div className="actions">
        <button type="submit" disabled={pending}>{pending ? "Esperando respuesta…" : "Enviar"}</button>
      </div>
      <div aria-live="polite">{request && <RequestResult request={request} />}</div>
    </form>
  );
}

function RequestResult({ request: r }: { request: AiRequest }) {
  if (r.status === "COMPLETED") {
    return (
      <div className="output">
        <p className="muted small">
          {r.model} · {r.latencyMs ?? "?"} ms · {r.inputTokens ?? "?"}→{r.outputTokens ?? "?"} tokens
        </p>
        <p className="pre">{r.outputText}</p>
      </div>
    );
  }
  if (r.status === "FAILED" || r.status === "TIMED_OUT") {
    return <p className="error" role="alert">{r.errorCode}: {r.errorMessage}</p>;
  }
  return null;
}
