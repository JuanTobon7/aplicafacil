import { useMemo, useState } from "react";
import { aiApi, type ProviderConfig } from "../api/ai";
import { PromptTester } from "../components/PromptTester";
import { ProviderForm } from "../components/ProviderForm";
import { ProviderItem } from "../components/providers/ProviderItem";
import { useProviderConfigs } from "../hooks/useProviderConfigs";

type Mode = { kind: "list" } | { kind: "create" } | { kind: "edit"; config: ProviderConfig };

/** Proveedores de IA de la persona: lista, alta/edición y prueba de un prompt. */
export function AiProvidersPage() {
  const { types, configs, error, busy, run, reload } = useProviderConfigs();
  const [mode, setMode] = useState<Mode>({ kind: "list" });
  // Un mapa por tipo, una vez: cada item lo consulta en O(1) en vez de recorrer la lista de tipos
  const typeNames = useMemo(() => new Map(types.map((t) => [t.type, t.displayName])), [types]);

  const backToList = async () => {
    setMode({ kind: "list" });
    await reload();
  };

  if (mode.kind !== "list") {
    return (
      <ProviderForm
        types={types}
        editing={mode.kind === "edit" ? mode.config : undefined}
        onCancel={() => setMode({ kind: "list" })}
        onCreate={async (c) => { await aiApi.create(c); await backToList(); }}
        onUpdate={async (id, changes) => { await aiApi.update(id, changes); await backToList(); }}
      />
    );
  }

  return (
    <>
      <section className="card">
        <div className="section-head">
          <h1>Proveedores de IA</h1>
          <button onClick={() => setMode({ kind: "create" })} disabled={!types.length}>Agregar proveedor</button>
        </div>
        <p className="muted small">
          Tus API keys se guardan cifradas en este equipo y nunca se vuelven a mostrar. Las usa el servidor MCP local.
        </p>
        {error && <p className="error" role="alert">{error}</p>}
        {configs === null && <p className="muted">Cargando…</p>}
        {configs?.length === 0 && <p>Todavía no configuraste ningún proveedor.</p>}
        <ul className="providers">
          {configs?.map((c) => (
            <ProviderItem key={c.id} config={c} typeName={typeNames.get(c.providerType) ?? c.providerType} busy={busy === c.id} run={run}
              onEdit={() => setMode({ kind: "edit", config: c })} />
          ))}
        </ul>
      </section>
      {configs && <PromptTester configs={configs} />}
    </>
  );
}
