import { FieldType, FormField, FormFieldOption } from "./types/forms";
import { BaseFieldProcessor } from "./base/BaseFieldProcessor";

export class RadioGroupProcessor extends BaseFieldProcessor {
  canProcess(element: Element): boolean {
    return element.tagName === 'FIELDSET' && 
           !!element.querySelector('legend') && 
           !!element.querySelector('input[type="radio"]');
  }

  process(element: Element): FormField | null {
    const fieldset = element as HTMLElement;
    if (fieldset.offsetParent === null) return null;

    const legend = fieldset.querySelector('legend');
    const label = legend?.textContent?.trim() || "";
    const description = this.findFieldsetDescription(fieldset);
    const radios = fieldset.querySelectorAll<HTMLInputElement>('input[type="radio"]');
    
    if (radios.length === 0) return null;

    const options: FormFieldOption[] = [];
    let name = "";
    let required = false;

    radios.forEach(radio => {
      if (!name) name = radio.name;
      if (radio.required) required = true;
      const radioLabel = document.querySelector(`label[for="${radio.id}"]`)?.textContent?.trim() || radio.value;
      options.push({ value: radio.value, label: radioLabel });
    });

    return {
      label,
      name,
      type: "radio",
      required,
      description,
      fieldType: FieldType.RADIO,
      options
    };
  }

  private findFieldsetDescription(fieldset: Element): string | undefined {
    return fieldset.querySelector('legend p, p')?.textContent?.trim();
  }
}