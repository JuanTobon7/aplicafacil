import { useId, useState, type ReactNode } from "react";

export type FieldErrors = Partial<Record<string, string>>;

/** Errores de un formulario y los atributos ARIA que enlazan cada input con su mensaje. */
export function useFieldErrors() {
  const ids = useId();
  const [errors, setErrors] = useState<FieldErrors>({});
  const errorId = (name: string) => `${ids}-${name}-error`;
  const aria = (name: string) =>
    errors[name] ? { "aria-invalid": true as const, "aria-describedby": errorId(name) } : {};
  return { errors, setErrors, errorId, aria };
}

type FieldProps = {
  label: ReactNode;
  error?: string;
  errorId?: string;
  hint?: ReactNode;
  children: ReactNode;
};

/** Etiqueta, control, ayuda y error, con el mismo marcado en todos los formularios. */
export function Field({ label, error, errorId, hint, children }: FieldProps) {
  return (
    <label className={error ? "invalid" : undefined}>
      {label}
      {children}
      {hint && <span className="muted small">{hint}</span>}
      {error && <span id={errorId} className="error small">{error}</span>}
    </label>
  );
}
