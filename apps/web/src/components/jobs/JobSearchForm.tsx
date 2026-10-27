import { useState, type FormEvent } from "react";
import { ApiError } from "../../api/http";
import { jobsApi, type JobPlatform, type NewJobSearch } from "../../api/jobs";
import type { SearchSettings } from "../../api/profile";
import { Field, useFieldErrors, type FieldErrors } from "../form/Field";
import { PLATFORMS } from "./labels";

type Values = { platform: JobPlatform; keywords: string; location: string; maxResults: string; days: string; remote: boolean };

const INITIAL: Values = { platform: "COMPUTRABAJO", keywords: "", location: "", maxResults: "25", days: "", remote: false };

/** Lo que filtran los portales: hoy, 3 días, semana, 15 días, mes (LinkedIn acepta cualquier número). */
const POSTED_OPTIONS = [1, 3, 7, 15, 30];

function isIntBetween(value: number, min: number, max: number): boolean {
  return Number.isInteger(value) && value >= min && value <= max;
}

export function validateJobSearch(v: Values): FieldErrors {
  const e: FieldErrors = {};
  const k = v.keywords.trim();
  if (k.length < 2 || k.length > 200) e.keywords = "Escribe entre 2 y 200 caracteres.";
  if (!isIntBetween(Number(v.maxResults), 1, 200)) e.maxResults = "Entero entre 1 y 200.";
  if (v.days.trim() !== "" && !isIntBetween(Number(v.days), 1, 30)) e.days = "Entre 1 y 30 días.";
  return e;
}

function toRequest(v: Values): NewJobSearch {
  return {
    platform: v.platform,
    keywords: v.keywords.trim(),
    location: v.remote ? null : v.location.trim() || null,
    maxResults: Number(v.maxResults),
    postedWithinDays: v.days.trim() ? Number(v.days) : null,
    remote: v.remote,
  };
}

/** La búsqueda guardada en el perfil (elegida por la IA) llega como valores iniciales. */
function initialValues(prefill?: SearchSettings): Values {
  if (!prefill) return INITIAL;
  return {
    ...INITIAL,
    keywords: prefill.keywords,
    location: prefill.location ?? "",
    days: prefill.postedWithinDays?.toString() ?? "",
    remote: prefill.remoteOnly,
  };
}

type Props = { onQueued: (searchId: string) => Promise<void>; prefill?: SearchSettings };

/** Pide una búsqueda; el worker la procesa en segundo plano. */
export function JobSearchForm({ onQueued, prefill }: Props) {
  const [values, setValues] = useState<Values>(() => initialValues(prefill));
  const fieldErrors = useFieldErrors();
  const [formError, setFormError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const found = validateJobSearch(values);
    fieldErrors.setErrors(found);
    setFormError(null);
    if (Object.keys(found).length) return;
    setSending(true);
    try {
      const created = await jobsApi.request(toRequest(values));
      await onQueued(created.id);
    } catch (e) {
      setFormError(e instanceof ApiError ? e.message : "No se pudo encolar la búsqueda.");
    } finally {
      setSending(false);
    }
  };

  return (
    <form className="card" onSubmit={(e) => void submit(e)} noValidate aria-busy={sending}>
      <JobSearchFields values={values} setValues={setValues} fieldErrors={fieldErrors} />
      {formError && <p className="error" role="alert">{formError}</p>}
      <p className="muted small">
        Corre en segundo plano: se abre un navegador sin ventana, se recorren las ofertas del listado y se guardan.
      </p>
      <div className="actions">
        <button type="submit" disabled={sending}>{sending ? "Encolando…" : "Buscar"}</button>
      </div>
    </form>
  );
}

type FieldsProps = {
  values: Values;
  setValues: (update: (v: Values) => Values) => void;
  fieldErrors: ReturnType<typeof useFieldErrors>;
};

function JobSearchFields({ values, setValues, fieldErrors: { errors, errorId, aria } }: FieldsProps) {
  const set = (name: keyof Values) => (value: string | boolean) => setValues((v) => ({ ...v, [name]: value }));
  return (
    <div className="grid">
      <Field label="Portal" hint={PLATFORMS.find((p) => p.value === values.platform)?.hint}>
        <select value={values.platform} onChange={(e) => set("platform")(e.target.value)}>
          {PLATFORMS.map((p) => <option key={p.value} value={p.value}>{p.label}</option>)}
        </select>
      </Field>
      <Field label="Cargo o palabras clave" error={errors.keywords} errorId={errorId("keywords")}>
        <input value={values.keywords} maxLength={200} placeholder="Desarrollador Java" onChange={(e) => set("keywords")(e.target.value)} {...aria("keywords")} />
      </Field>
      <Field label="Ciudad (opcional)">
        <input value={values.location} maxLength={120} placeholder="Bogotá" disabled={values.remote} onChange={(e) => set("location")(e.target.value)} />
      </Field>
      <Field label="Máximo de ofertas" error={errors.maxResults} errorId={errorId("maxResults")}>
        <input inputMode="numeric" value={values.maxResults} onChange={(e) => set("maxResults")(e.target.value)} {...aria("maxResults")} />
      </Field>
      <Field label="Publicadas hace como mucho" error={errors.days} errorId={errorId("days")}>
        <select value={values.days} onChange={(e) => set("days")(e.target.value)} {...aria("days")}>
          <option value="">Cualquier fecha</option>
          {POSTED_OPTIONS.map((d) => <option key={d} value={d}>{d === 1 ? "24 horas" : `${d} días`}</option>)}
        </select>
      </Field>
      <label><input type="checkbox" checked={values.remote} onChange={(e) => set("remote")(e.target.checked)} /> Solo remoto</label>
    </div>
  );
}
