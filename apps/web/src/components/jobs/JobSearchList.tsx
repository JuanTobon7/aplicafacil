import type { JobSearch } from "../../api/jobs";
import { badgeClass, ERROR_HINT, platformLabel, STATUS_LABEL } from "./labels";

type Props = {
  searches: JobSearch[] | null;
  loadError: string | null;
  selected: string | null;
  onToggle: (searchId: string) => void;
};

/** "Mis búsquedas": estado de cada una; al elegir una completada se ven sus ofertas. */
export function JobSearchList({ searches, loadError, selected, onToggle }: Props) {
  return (
    <section className="card">
      <h2>Mis búsquedas</h2>
      {loadError && <p className="error" role="alert">{loadError}</p>}
      {searches === null && !loadError && <p className="muted">Cargando…</p>}
      {searches?.length === 0 && <p className="muted">Todavía no hay búsquedas. Prueba «Buscar con IA».</p>}
      <ul className="providers" aria-live="polite">
        {searches?.map((s) => (
          <JobSearchItem key={s.id} search={s} selected={s.id === selected} onToggle={() => onToggle(s.id)} />
        ))}
      </ul>
    </section>
  );
}

function JobSearchItem({ search: s, selected, onToggle }: { search: JobSearch; selected: boolean; onToggle: () => void }) {
  return (
    <li className={selected ? "selected" : undefined}>
      <div className="provider-head">
        <button className="link" onClick={onToggle} aria-expanded={selected}>
          <strong>{s.keywords}</strong>
          {s.location && <> · {s.location}</>}
        </button>
        <span>
          {s.planId && <span className="badge ai" title="La eligió la IA desde tu perfil">IA</span>}{" "}
          <span className={`badge status-${badgeClass(s.status)}`}>{STATUS_LABEL[s.status]}</span>
        </span>
      </div>
      <p className="muted small">
        {platformLabel(s.platform)} · {new Date(s.requestedAt).toLocaleString()}
        <SearchProgress search={s} />
      </p>
      <SearchError search={s} />
    </li>
  );
}

function SearchProgress({ search: s }: { search: JobSearch }) {
  if (s.status === "COMPLETED") return <> · {s.foundCount} ofertas ({s.newCount} nuevas)</>;
  if (s.status === "QUEUED" && s.attempts > 0 && s.nextAttemptAt) {
    return <> · reintento {s.attempts + 1} a las {new Date(s.nextAttemptAt).toLocaleTimeString()}</>;
  }
  return null;
}

/** El error definitivo (FAILED) o el del último intento mientras espera reintentar. */
function SearchError({ search: s }: { search: JobSearch }) {
  const failed = s.status === "FAILED";
  if (!failed && !(s.status === "QUEUED" && s.errorCode)) return null;
  const hint = s.errorCode ? ERROR_HINT[s.errorCode] : undefined;
  return <p className={`small ${failed ? "error" : "muted"}`}>{hint ?? s.errorMessage ?? s.errorCode}</p>;
}
