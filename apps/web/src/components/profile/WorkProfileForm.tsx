import { useState, type FormEvent } from "react";
import { ApiError } from "../../api/http";
import { profileApi, type Seniority, type WorkProfile, type WorkProfileDefinition, type WorkRole } from "../../api/profile";
import { Field, useFieldErrors } from "../form/Field";
import { MAX_SECONDARY_ROLES, SENIORITIES, WORK_ROLES } from "./workRoles";

type Props = { initial: WorkProfile | null; onCancel: () => void; onSaved: () => Promise<void> };

function draftOf(initial: WorkProfile | null): WorkProfileDefinition {
  if (!initial) return { primaryRole: "BACKEND", secondaryRoles: [], seniority: null, title: null };
  return { primaryRole: initial.primaryRole, secondaryRoles: initial.secondaryRoles, seniority: initial.seniority, title: initial.title };
}

/** Elegir el rol principal, hasta 3 secundarios, el seniority y cómo te presentas. */
export function WorkProfileForm({ initial, onCancel, onSaved }: Props) {
  const [draft, setDraft] = useState<WorkProfileDefinition>(() => draftOf(initial));
  const { errors, setErrors, errorId, aria } = useFieldErrors();
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const other = draft.primaryRole === "OTHER";

  const setPrimary = (role: WorkRole) =>
    setDraft((d) => ({ ...d, primaryRole: role, secondaryRoles: d.secondaryRoles.filter((r) => r !== role) }));

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const title = draft.title?.trim() || null;
    if (other && !title) {
      setErrors({ title: "Escribe tu perfil (por ejemplo, Contador público)." });
      return;
    }
    setSaving(true);
    try {
      await profileApi.defineWorkProfile({ ...draft, title });
      await onSaved();
    } catch (e) {
      setFormError(e instanceof ApiError ? e.message : "No se pudo guardar tu perfil laboral.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <form className="card" onSubmit={(e) => void submit(e)} noValidate aria-busy={saving}>
      <h2>Perfil laboral</h2>
      <div className="grid">
        <Field label="Perfil principal" hint={WORK_ROLES.find((r) => r.value === draft.primaryRole)?.hint}>
          <select value={draft.primaryRole} onChange={(e) => setPrimary(e.target.value as WorkRole)}>
            {WORK_ROLES.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
          </select>
        </Field>
        <Field label="Seniority">
          <select value={draft.seniority ?? ""} onChange={(e) => setDraft((d) => ({ ...d, seniority: (e.target.value || null) as Seniority | null }))}>
            <option value="">Sin indicar</option>
            {SENIORITIES.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
          </select>
        </Field>
        <Field label={other ? "¿Cuál es tu perfil?" : "Cómo te presentas (opcional)"} error={errors.title} errorId={errorId("title")}>
          <input value={draft.title ?? ""} maxLength={120} placeholder={other ? "Contador público" : "Desarrollador backend Java"}
            onChange={(e) => setDraft((d) => ({ ...d, title: e.target.value }))} {...aria("title")} />
        </Field>
      </div>
      <SecondaryRoles draft={draft} onChange={(secondaryRoles) => setDraft((d) => ({ ...d, secondaryRoles }))} />
      {formError && <p className="error" role="alert">{formError}</p>}
      <div className="actions">
        <button type="button" className="secondary" onClick={onCancel} disabled={saving}>Cancelar</button>
        <button type="submit" disabled={saving}>{saving ? "Guardando…" : "Guardar"}</button>
      </div>
    </form>
  );
}

/** Hasta 3 roles más (alguien Backend que también hace DevOps); el principal no se repite. */
function SecondaryRoles({ draft, onChange }: { draft: WorkProfileDefinition; onChange: (roles: WorkRole[]) => void }) {
  const full = draft.secondaryRoles.length >= MAX_SECONDARY_ROLES;
  const toggle = (role: WorkRole) =>
    onChange(draft.secondaryRoles.includes(role) ? draft.secondaryRoles.filter((r) => r !== role) : [...draft.secondaryRoles, role]);
  return (
    <fieldset className="roles">
      <legend>También trabajas como (hasta {MAX_SECONDARY_ROLES})</legend>
      {WORK_ROLES.filter((r) => r.value !== draft.primaryRole && r.value !== "OTHER").map((r) => {
        const checked = draft.secondaryRoles.includes(r.value);
        return (
          <label key={r.value} className="checkbox">
            <input type="checkbox" checked={checked} disabled={!checked && full} onChange={() => toggle(r.value)} /> {r.label}
          </label>
        );
      })}
    </fieldset>
  );
}
