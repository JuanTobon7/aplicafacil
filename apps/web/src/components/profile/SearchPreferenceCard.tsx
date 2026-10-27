import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router";
import { ApiError } from "../../api/http";
import { profileApi, type SearchPreference } from "../../api/profile";
import { Field } from "../form/Field";

/** Lo que se puede filtrar en los portales: hoy, 3 días, semana, 15 días, mes. */
const POSTED_OPTIONS = [1, 3, 7, 15, 30];

type Props = {
  preference: SearchPreference;
  configId: string | null;
  pending: boolean;
  onChanged: () => Promise<void>;
};

/**
 * La búsqueda de empleo del perfil: la IA elige las palabras clave; la persona decide si solo remoto, qué tan reciente
 * y dónde. "Buscar vacantes" abre /vacantes con todo esto ya puesto.
 */
export function SearchPreferenceCard({ preference: p, configId, pending, onChanged }: Props) {
  const navigate = useNavigate();
  const [keywords, setKeywords] = useState(p.keywords ?? "");
  const [remoteOnly, setRemoteOnly] = useState(p.remoteOnly);
  const [days, setDays] = useState(p.postedWithinDays?.toString() ?? "");
  const [location, setLocation] = useState(p.location ?? "");
  const [error, setError] = useState<string | null>(null);
  const settings = { keywords: keywords.trim(), remoteOnly, postedWithinDays: days ? Number(days) : null, location: location.trim() || null };

  const run = async (action: () => Promise<unknown>) => {
    setError(null);
    try {
      await action();
      await onChanged();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "No se pudo guardar.");
    }
  };
  const save = (event: FormEvent) => {
    event.preventDefault();
    void run(() => profileApi.configureSearch(settings));
  };
  const search = () => void run(async () => {
    await profileApi.configureSearch(settings);
    navigate("/vacantes", { state: { prefill: settings } });
  });

  return (
    <form className="card" onSubmit={save} aria-busy={pending}>
      <h2>Mi búsqueda de empleo</h2>
      {p.rationale && <p className="muted small">{p.rationale}</p>}
      <div className="grid">
        <Field label="Palabras clave (elegidas por la IA, puedes cambiarlas)">
          <input value={keywords} maxLength={200} placeholder="desarrollador backend java spring boot" onChange={(e) => setKeywords(e.target.value)} />
        </Field>
        <Field label="Publicadas hace como mucho">
          <select value={days} onChange={(e) => setDays(e.target.value)}>
            <option value="">Cualquier fecha</option>
            {POSTED_OPTIONS.map((d) => <option key={d} value={d}>{d === 1 ? "24 horas" : `${d} días`}</option>)}
          </select>
        </Field>
        <Field label="Ciudad (opcional)">
          <input value={location} maxLength={120} disabled={remoteOnly} onChange={(e) => setLocation(e.target.value)} />
        </Field>
        <label><input type="checkbox" checked={remoteOnly} onChange={(e) => setRemoteOnly(e.target.checked)} /> Solo remoto</label>
      </div>
      <Alternatives list={p.alternatives} onPick={setKeywords} />
      {error && <p className="error" role="alert">{error}</p>}
      <div className="actions">
        <button type="button" onClick={search} disabled={keywords.trim().length < 2}>Buscar vacantes</button>
        <button type="submit" className="secondary" disabled={keywords.trim().length < 2}>Guardar</button>
        {configId && (
          <button type="button" className="secondary" disabled={pending} onClick={() => void run(() => profileApi.suggestSearch(configId))}>
            Pedir otra sugerencia a la IA
          </button>
        )}
      </div>
    </form>
  );
}

function Alternatives({ list, onPick }: { list: string[]; onPick: (value: string) => void }) {
  if (list.length === 0) return null;
  return (
    <p className="small">
      <span className="muted">Otras opciones: </span>
      {list.map((a) => <button key={a} type="button" className="link" onClick={() => onPick(a)}>{a}</button>)}
    </p>
  );
}
