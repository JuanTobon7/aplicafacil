import { FieldType, FormField } from "./types/forms";
import { BaseFieldProcessor } from "./base/BaseFieldProcessor";

export class StandardInputProcessor extends BaseFieldProcessor {
  canProcess(element: Element): boolean {
    return element.matches('input:not([type="hidden"]):not([type="radio"]):not([type="checkbox"]), textarea, select');
  }

  process(element: Element): FormField | null {
    const input = element as HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement;
    const isFile = input instanceof HTMLInputElement && input.type === 'file';
    
    // Validaciones de visibilidad y CV
    if (!isFile && input.offsetParent === null) return null;
    if (isFile && !this.isResumeFileInput(input as HTMLInputElement)) return null;

    const label = this.findLabel(input);
    const description = this.findDescription(input);
    const placeholder = (input instanceof HTMLInputElement || input instanceof HTMLTextAreaElement) 
      ? input.placeholder 
      : undefined;

    const options = input instanceof HTMLSelectElement
      ? Array.from(input.options).map(opt => ({ value: opt.value, label: opt.text.trim() }))
      : [];

    return {
      label,
      name: input.name || input.id || "",
      type: input instanceof HTMLSelectElement ? "select" : input.type,
      required: input.required,
      placeholder,
      description,
      fieldType: FieldType.UNKNOWN, // se asignará después con el detector
      ...(options.length ? { options } : {})
    };
  }
}