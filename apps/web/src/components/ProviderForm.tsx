import { useId, useState, type FormEvent } from "react";
import type { NewProviderConfig, ProviderChanges, ProviderConfig, ProviderType } from "../api/ai";
import { ApiError } from "../api/http";
import { Field, useFieldErrors } from "./form/Field";
import { ApiKeyField, ModelFields, ParameterFields, type FieldsProps } from "./providers/ProviderFields";
import {
  initialValues,
  toChanges,
  toNewConfig,
  validateProviderForm,
  withProviderType,
  type ProviderFormValues,
} from "./providers/providerFormModel";

type Props = {
  types: ProviderType[];
  /** Sin `editing` es un alta (pide la API key); con `editing` se cambian datos, no la key. */
  editing?: ProviderConfig;
  onCreate: (config: NewProviderConfig) => Promise<void>;
  onUpdate: (id: string, changes: ProviderChanges) => Promise<void>;
  onCancel: () => void;
};

/** Alta y edición de una configuración. Reglas y conversión en `providerFormModel`; aquí solo estado y vista. */
export function ProviderForm({ types, editing, onCreate, onUpdate, onCancel }: Props) {
  const listId = `${useId()}-models`;
  const [values, setValues] = useState<ProviderFormValues>(() => initialValues(types, editing));
  const { errors, setErrors, errorId, aria } = useFieldErrors();
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const type = types.find((t) => t.type === values.providerType);
  const models = Array.from(new Set([...(type?.suggestedModels ?? []), ...(editing?.availableModels ?? [])]));
  const set = (name: keyof ProviderFormValues) => (value: string) => setValues((v) => ({ ...v, [name]: value }));
  const fields: FieldsProps = { values, set, errors, errorId, aria };

  const save = () => (editing ? onUpdate(editing.id, toChanges(values, editing)) : onCreate(toNewConfig(values)));

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const found = validateProviderForm(values, !editing);
    setErrors(found);
    setFormError(null);
    if (Object.keys(found).length) return;
    setSaving(true);
    try {
      await save();
      setValues((v) => ({ ...v, apiKey: "" }));
    } catch (e) {
      setErrors(e instanceof ApiError ? e.fieldErrors : {});
      setFormError(e instanceof ApiError ? e.message : "No se pudo guardar.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <form className="card provider-form" onSubmit={(e) => void submit(e)} noValidate aria-busy={saving}>
      <h2>{editing ? `Editar "${editing.displayName}"` : "Agregar proveedor"}</h2>
      <div className="grid">
        <Field label="Proveedor">
          <select value={values.providerType} disabled={!!editing} onChange={(e) => setValues((v) => withProviderType(v, types, e.target.value))}>
            {types.map((t) => <option key={t.type} value={t.type}>{t.displayName}</option>)}
          </select>
        </Field>
        <Field label="Nombre" error={errors.displayName} errorId={errorId("displayName")}>
          <input value={values.displayName} maxLength={100} onChange={(e) => set("displayName")(e.target.value)} {...aria("displayName")} />
        </Field>
        {!editing && <ApiKeyField {...fields} />}
        <ModelFields {...fields} type={type} models={models} listId={listId} />
        <ParameterFields {...fields} />
      </div>
      {!editing && (
        <label className="checkbox">
          <input type="checkbox" checked={values.enabled} onChange={(e) => setValues((v) => ({ ...v, enabled: e.target.checked }))} /> Activar al guardar
        </label>
      )}
      {formError && <p className="error" role="alert">{formError}</p>}
      <div className="actions">
        <button type="button" className="secondary" onClick={onCancel} disabled={saving}>Cancelar</button>
        <button type="submit" disabled={saving}>{saving ? "Guardando…" : "Guardar"}</button>
      </div>
    </form>
  );
}
