import {
    FieldType,
    FormField,
    JobForm
} from "../../types/forms";

import { Reader } from "../base/Reader";

export class GeneralContentReader implements Reader {

    readContent(): JobForm {

        const fields: FormField[] = [];

        const form = document.querySelector<HTMLFormElement>('form');
        if (!form) {
            return { url: window.location.href, title: document.title, fields: [] };
        }

        const elements = form.querySelectorAll(
            'input:not([type="hidden"]), textarea, select'
        );

        elements.forEach(el => {

            const input = el as HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement;

            if (input.offsetParent === null) {
                return;
            }

            const label = this.findLabel(input);

            const placeholder =
                input instanceof HTMLInputElement ||
                input instanceof HTMLTextAreaElement
                    ? input.placeholder
                    : undefined;

            fields.push({
                label,
                name:
                    input.name ||
                    input.id ||
                    "",
                type:
                    input instanceof HTMLSelectElement
                        ? "select"
                        : input.type,
                required:
                    input.required,
                placeholder,
                fieldType:
                    this.normalizeField(
                        label,
                        input.name ||
                        input.id ||
                        ""
                    )
            });

        });

        return {
            url: window.location.href,
            title: document.title,
            fields
        };
    }

    available(): boolean {

        const form = document.querySelector<HTMLFormElement>('form');
        if (!form) {
            return false;
        }

        const fields = form.querySelectorAll(
            'input:not([type="hidden"]), textarea, select'
        );

        const visibleFields = [...fields].filter(field => {

            const element = field as HTMLElement;

            return (
                element.offsetParent !== null &&
                !element.hasAttribute("disabled")
            );
        });

        return visibleFields.length >= 3;
    }

    private findLabel(
        element:
            HTMLInputElement |
            HTMLTextAreaElement |
            HTMLSelectElement
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

    private normalizeField(
        label: string,
        name: string
    ): FieldType {

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

        if (text.includes("portfolio")) {
            return FieldType.PORTFOLIO;
        }

        if (
            text.includes("resume") ||
            text.includes("curriculum") ||
            text.includes("cv")
        ) {
            return FieldType.CV;
        }

        if (
            text.includes("first name") ||
            text.includes("nombre")
        ) {
            return FieldType.FIRST_NAME;
        }

        if (
            text.includes("last name") ||
            text.includes("apellido")
        ) {
            return FieldType.LAST_NAME;
        }

        if (
            text.includes("country") ||
            text.includes("país") ||
            text.includes("pais")
        ) {
            return FieldType.COUNTRY;
        }

        return FieldType.UNKNOWN;
    }
}