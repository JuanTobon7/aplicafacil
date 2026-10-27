import { useState } from "react";
import type { ProviderType } from "../../api/ai";
import { Field, type FieldErrors } from "../form/Field";
import type { ProviderFormValues } from "./providerFormModel";

/** Lo que cada grupo de campos necesita del formulario. */
export type FieldsProps = {
  values: ProviderFormValues;
  set: (name: keyof ProviderFormValues) => (value: string) => void;
  errors: FieldErrors;
  errorId: (name: string) => string;
  aria: (name: string) => object;
};

/** La key solo vive en el estado del formulario hasta enviarla; nunca se vuelve a mostrar. */
export function ApiKeyField({ values, set, errors, errorId, aria }: FieldsProps) {
  const [showKey, setShowKey] = useState(false);
  return (
    <Field label="API key" error={errors.apiKey} errorId={errorId("apiKey")} hint="Se guarda cifrada y no se vuelve a mostrar.">
      <span className="input-row">
        <input
          type={showKey ? "text" : "password"}
          value={values.apiKey}
          autoComplete="off"
          spellCheck={false}
          autoCapitalize="off"
          onChange={(e) => set("apiKey")(e.target.value)}
          {...aria("apiKey")}
        />
        <button type="button" className="secondary" onClick={() => setShowKey((v) => !v)} aria-pressed={showKey}>
          {showKey ? "Ocultar" : "Mostrar"}
        </button>
      </span>
    </Field>
  );
}

export function ModelFields({ values, set, errors, errorId, aria, type, models, listId }: FieldsProps & {
  type?: ProviderType;
  models: string[];
  listId: string;
}) {
  return (
    <>
      <Field label="Modelo">
        <input value={values.model} list={listId} onChange={(e) => set("model")(e.target.value)} placeholder={type?.defaultModel} />
        <datalist id={listId}>{models.map((m) => <option key={m} value={m} />)}</datalist>
      </Field>
      {type?.allowsBaseUrl && (
        <Field label="URL de la API (opcional)" error={errors.baseUrl} errorId={errorId("baseUrl")}
          hint="Para APIs compatibles con OpenAI (Ollama, LM Studio…).">
          <input value={values.baseUrl} placeholder="https://api.openai.com/v1" onChange={(e) => set("baseUrl")(e.target.value)} {...aria("baseUrl")} />
        </Field>
      )}
    </>
  );
}

const PARAMETERS: { name: "temperature" | "maxTokens" | "topP"; label: string; inputMode: "decimal" | "numeric" }[] = [
  { name: "temperature", label: "Temperatura", inputMode: "decimal" },
  { name: "maxTokens", label: "Máximo de tokens", inputMode: "numeric" },
  { name: "topP", label: "Top P", inputMode: "decimal" },
];

/** Parámetros del modelo; vacíos = los del proveedor. */
export function ParameterFields({ values, set, errors, errorId, aria }: FieldsProps) {
  return (
    <>
      {PARAMETERS.map(({ name, label, inputMode }) => (
        <Field key={name} label={label} error={errors[name]} errorId={errorId(name)}>
          <input inputMode={inputMode} value={values[name]} placeholder="por defecto" onChange={(e) => set(name)(e.target.value)} {...aria(name)} />
        </Field>
      ))}
    </>
  );
}
