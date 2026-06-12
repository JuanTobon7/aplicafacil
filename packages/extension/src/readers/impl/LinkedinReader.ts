import { FieldType, FormField, JobForm } from "../../types/forms";
import { Reader } from "../base/Reader";

export class LinkedinReader implements Reader {

    available(): boolean {
        return (
            location.hostname.includes("linkedin.com")
            &&
            location.href.includes("/jobs/")
        );
    }

    readContent(): JobForm {
        const fields: FormField[] = [];

        const form = document.querySelector<HTMLFormElement>('form');
        if (!form) {
            return { url: location.href, title: document.title, fields: [] };
        }

        const elements = form.querySelectorAll(
            'input:not([type="hidden"]), textarea, select'
        );

        elements.forEach(el => {
            const element = el as HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement;

            if (element.offsetParent === null) return;

            const label = this.findLabel(element);

            fields.push({
                label,
                name: element.name || element.id,
                type: element.tagName === 'SELECT' ? 'select' : (element as HTMLInputElement).type,
                required: element.required,
                placeholder:
                    element instanceof HTMLInputElement || element instanceof HTMLTextAreaElement
                        ? element.placeholder
                        : undefined,
                fieldType: this.normalizeField(label, element.name || element.id),
            });
        });

        return {
            url: location.href,
            title: document.title,
            fields,
        };
    }

    private findLabel(
        element: HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement
    ): string {

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

    private normalizeField(label: string, name: string): FieldType {

        const text = `${label} ${name}`.toLowerCase();

        if (text.includes("email")) {
            return FieldType.EMAIL;
        }

        if (
            text.includes("phone") ||
            text.includes("telefono") ||
            text.includes("teléfono") ||
            text.includes("móvil") ||
            text.includes("movil")
        ) {
            return FieldType.PHONE;
        }

        if (text.includes("linkedin")) {
            return FieldType.LINKEDIN;
        }

        if (
            text.includes("resume") ||
            text.includes("curriculum") ||
            text.includes("cv")
        ) {
            return FieldType.CV;
        }

        if (
            text.includes("country") ||
            text.includes("país") ||
            text.includes("pais") ||
            text.includes("código del país")
        ) {
            return FieldType.COUNTRY;
        }

        return FieldType.UNKNOWN;
    }
}