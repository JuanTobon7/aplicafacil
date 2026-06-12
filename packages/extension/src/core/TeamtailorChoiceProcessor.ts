import { FieldType, FormField, FormFieldOption } from "./types/forms";
import { BaseFieldProcessor } from "./base/BaseFieldProcessor";

export class TeamtailorChoiceProcessor extends BaseFieldProcessor {
  canProcess(element: Element): boolean {
    return element.matches('[data-controller*="forms--inputs--choice"]');
  }

  process(element: Element): FormField | null {
    const container = element as HTMLElement;
    if (container.offsetParent === null) return null;

    const fieldset = container.closest('fieldset');
    const legend = fieldset?.querySelector('legend');
    const label = legend?.textContent?.trim() || "";
    const radios = container.querySelectorAll<HTMLInputElement>('input[type="radio"]');
    
    let name = "", required = false;
    const options: FormFieldOption[] = [];

    radios.forEach(radio => {
      if (!name) name = radio.name;
      if (radio.required) required = true;
      const labelText = document.querySelector(`label[for="${radio.id}"]`)?.textContent?.trim() || radio.value;
      options.push({ value: radio.value, label: labelText });
    });

    // Fallback a data-search-text
    if (options.length === 0) {
      container.querySelectorAll('[data-search-text]').forEach(item => {
        const text = item.getAttribute('data-search-text') || item.textContent?.trim() || "";
        const value = item.getAttribute('data-value') || text;
        if (text) options.push({ value, label: text });
      });
    }

    if (options.length === 0) return null;

    return {
      label,
      name,
      type: "radio",
      required,
      description: this.findDescription(fieldset || container),
      fieldType: FieldType.RADIO,
      options
    };
  }
}