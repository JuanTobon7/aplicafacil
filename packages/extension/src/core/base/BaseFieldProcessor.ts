import { FieldType, FormField } from "../types/forms";
import { FieldProcessor } from "./FieldFieldProcessor";

export abstract class BaseFieldProcessor implements FieldProcessor {
  protected findLabel(element: HTMLElement): string {
    const id = element.id;

        if (id) {
            const label = document.querySelector(`label[for="${id}"]`);
            if (label?.textContent?.trim()) {
                return label.textContent.trim();
            }
        }

        const parentLabel = element.closest("label");
        if (parentLabel?.textContent?.trim()) {
            return parentLabel.textContent.trim();
        }

        const siblingLabel = element.parentElement?.querySelector("label");
        if (siblingLabel?.textContent?.trim()) {
            return siblingLabel.textContent.trim();
        }

        return "";
  }

  protected findDescription(element: HTMLElement): string | undefined {
    const ariaDescribedBy = element.getAttribute('aria-describedby');
        if (ariaDescribedBy) {
            for (const refId of ariaDescribedBy.split(' ')) {
                const refEl = document.getElementById(refId);
                const text = refEl?.textContent?.trim();
                if (text && !text.includes('error') && !text.includes('Error')) return text;
            }
        }

        // Busca un <p> hermano cercano
        const parent = element.closest('[data-controller*="forms--inputs"]') ||
            element.parentElement;
        const p = parent?.querySelector('p');
        if (p?.textContent?.trim()) return p.textContent.trim();

        return undefined;
  }

  protected isResumeFileInput(input: HTMLInputElement): boolean {
    // Workable: data-ui="resume"
        if (input.getAttribute('data-ui') === 'resume') return true;

        // Teamtailor: contenedor padre con id="upload_resume_field"
        if (input.closest('[id*="resume"]')) return true;

        // Por aria-labelledby
        const ariaLabel = this.findLabel(input).toLowerCase();
        if (
            ariaLabel.includes('resume') ||
            ariaLabel.includes('curriculum') ||
            ariaLabel.includes('currículum') ||
            ariaLabel.includes('cv')
        ) return true;

        // Por name/id
        const nameOrId = `${input.name} ${input.id}`.toLowerCase();
        if (
            nameOrId.includes('resume') ||
            nameOrId.includes('curriculum') ||
            nameOrId.includes('cv')
        ) return true;

        // Por accept: si acepta pdf/doc probablemente es CV
        const accept = input.accept?.toLowerCase() || '';
        if (accept.includes('.pdf') || accept.includes('.doc') || accept.includes('application/pdf')) {
            return true;
        }

        return false;
  }

  protected detectFieldType(
    label: string,
    name: string,
    inputType?: string,
    element?: HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement
    ): FieldType {
    // Casos directos por tipo de input
    if (inputType === 'file') return FieldType.CV;
    if (inputType === 'radio') return FieldType.RADIO;
    if (inputType === 'checkbox') return FieldType.CHECKBOX;
    if (inputType === 'textarea' || element instanceof HTMLTextAreaElement) {
        // Si es textarea y parece CV, lo marcamos como CV
        if (this.looksLikeResumeField(label, name)) return FieldType.CV;
        return FieldType.TEXTAREA;
    }

    const text = `${label} ${name}`.toLowerCase();

    // Detección por palabras clave (ya existente)
    if (text.includes("email") || text.includes("correo")) return FieldType.EMAIL;
    if (text.includes("phone") || text.includes("telefono") || text.includes("teléfono") ||
        text.includes("móvil") || text.includes("movil")) return FieldType.PHONE;
    if (text.includes("linkedin")) return FieldType.LINKEDIN;
    if (text.includes("portfolio")) return FieldType.PORTFOLIO;
    
    // CV: ahora también para textarea/input text
    if (this.looksLikeResumeField(label, name)) return FieldType.CV;

    if (text.includes("first name") || text.includes("nombre") || text.includes("first_name") || text.includes("firstname"))
        return FieldType.FIRST_NAME;
    if (text.includes("last name") || text.includes("apellido") || text.includes("last_name") || text.includes("lastname"))
        return FieldType.LAST_NAME;
    if (text.includes("country") || text.includes("país") || text.includes("pais"))
        return FieldType.COUNTRY;

    return FieldType.UNKNOWN;
    }

/**
 * Determina si el campo (por su label/name) parece ser un campo de currículum
 */
private looksLikeResumeField(label: string, name: string): boolean {
  const text = `${label} ${name}`.toLowerCase();
  return /(resume|curriculum|currículum|cv|hoja de vida)/i.test(text);
}

  abstract canProcess(element: Element, form: HTMLFormElement): boolean;
  abstract process(element: Element, form: HTMLFormElement): FormField | null;
}