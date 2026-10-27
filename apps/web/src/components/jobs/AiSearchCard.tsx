import { Link } from "react-router";
import { useAiSearchPlan, type PlanState } from "../../hooks/useAiSearchPlan";

/**
 * La forma principal de buscar: la IA lee el perfil (CV, experiencia, perfil laboral), elige las búsquedas y las
 * lanza en LinkedIn y Computrabajo. La búsqueda manual queda como alternativa.
 */
export function AiSearchCard({ onLaunched }: { onLaunched: () => Promise<void> }) {
  const { providers, state, start } = useAiSearchPlan(onLaunched);
  const thinking = state.phase === "thinking";

  return (
    <section className="card ai-hero" aria-live="polite">
      <div className="section-head">
        <h2><span aria-hidden>✨</span> Deja que la IA busque por ti</h2>
        <span className="badge ai">IA</span>
      </div>
      <p className="muted">
        Lee tu perfil, elige las búsquedas que mejor encajan contigo y las lanza en LinkedIn y Computrabajo. Las ofertas
        aparecen abajo en unos minutos.
      </p>
      <PlanStatus state={state} />
      <div className="actions">
        {providers !== null && providers.length === 0 ? (
          <Link className="button ai" to="/ia">Configura un proveedor de IA</Link>
        ) : (
          <button className="ai" onClick={() => void start()} disabled={providers === null || thinking}>
            {thinking ? <><span className="spinner" aria-hidden /> La IA está leyendo tu perfil…</> : "Buscar con IA"}
          </button>
        )}
        <Link className="button secondary" to="/perfil">Revisar mi perfil</Link>
      </div>
    </section>
  );
}

function PlanStatus({ state }: { state: PlanState }) {
  if (state.phase === "launched") {
    return <p className="ok">Listo: la IA eligió tus búsquedas y ya están en cola. Míralas en «Mis búsquedas».</p>;
  }
  if (state.phase === "failed") return <p className="error" role="alert">{state.message}</p>;
  return null;
}
