import { FormField } from "../types/forms";

export interface FieldProcessor {
  /** ¿Puede este procesador manejar este elemento? */
  canProcess(element: Element, form: HTMLFormElement): boolean;
  
  /** Procesa el elemento y devuelve un FormField (o null si no debe agregarse) */
  process(element: Element, form: HTMLFormElement): FormField | null;
}