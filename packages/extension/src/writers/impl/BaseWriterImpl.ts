import { Recommendation } from "../../api/recommendations";
import { IWriter } from "../base/IWriter";

// ------------------------------------------------------------------
// Clase base: encapsula el "cómo" se escribe en un elemento del DOM.
//
// Contrato con el servidor: cada Recommendation trae un `fieldName`
// (que coincide con el atributo `name` del control en el DOM) y un
// `value` con el texto/opción sugerido por el LLM.
//
// Los writers específicos por sitio solo necesitan sobreescribir
// available() y, si el sitio lo requiere, resolveElement().
// ------------------------------------------------------------------
export abstract class BaseWriter implements IWriter {
  abstract available(): boolean;

  writeRecommendations(recommendations: Recommendation[]): void {
    console.log("[AutoApply] Writing recommendations:", recommendations.length);

    recommendations.forEach((recommendation) => {
      // Sin valor recomendado → nada que escribir
      if (recommendation.value === null || recommendation.value === undefined) {
        console.log(`[AutoApply] Sin valor para "${recommendation.fieldName}", omitido`);
        return;
      }

      const element = this.resolveElement(recommendation.fieldName);

      if (!element) {
        console.log(`[AutoApply] No se encontró elemento para fieldName "${recommendation.fieldName}"`);
        return;
      }

      if (recommendation.requires_review) {
        console.warn(
          `[AutoApply] "${recommendation.fieldName}" requiere revisión (confianza ${recommendation.confidence}), aun así se autocompletará`
        );
      }

      this.writeToElement(element, recommendation);
    });
  }

  // Por defecto busca por atributo `name` (campo `fieldName` de la recomendación),
  // con fallback a id y a etiqueta visible. Los writers de sitios específicos
  // pueden sobreescribir esto si el sitio requiere otra estrategia.
  protected resolveElement(fieldName: string): HTMLElement | null {
    if (!fieldName) return null;
    const safeName = CSS.escape(fieldName);

    // 1) Por atributo name (el contrato del servidor)
    let element = document.querySelector<HTMLElement>(`[name="${safeName}"]`);
    if (element) return element;

    // 2) Por id
    element = document.getElementById(fieldName);
    if (element) return element;

    // 3) Por etiqueta visible que coincida con el nombre del campo
    const normalized = fieldName.toLowerCase().replace(/\s+/g, " ").trim();
    const labels = Array.from(document.querySelectorAll("label"));
    const matchingLabel = labels.find((label) => {
      const text = (label.textContent ?? "").toLowerCase().replace(/\s+/g, " ").trim();
      return text === normalized || text.includes(normalized);
    });

    if (matchingLabel?.htmlFor) {
      const byFor = document.getElementById(matchingLabel.htmlFor);
      if (byFor) return byFor;
    }

    const nested = matchingLabel?.querySelector<HTMLElement>("input, textarea, select");
    if (nested) return nested;

    return null;
  }

  protected writeToElement(element: HTMLElement, recommendation: Recommendation): void {
    const value = recommendation.value ?? "";

    // Radio: marcar la opción cuyo valor (o texto de label) coincida
    if (element instanceof HTMLInputElement && element.type === "radio") {
      this.selectRadio(element, value);
      return;
    }

    // Checkbox: marcar según si el valor sugiere afirmación
    if (element instanceof HTMLInputElement && element.type === "checkbox") {
      this.setCheckbox(element, value);
      return;
    }

    // Select: elegir opción por value o por texto
    if (element instanceof HTMLSelectElement) {
      this.setSelectValue(element, value);
      return;
    }

    // Inputs / textareas
    if (element instanceof HTMLInputElement || element instanceof HTMLTextAreaElement) {
      if (element.type === "file") {
        console.log(`[AutoApply] Campo file "${recommendation.fieldName}" no se autocompleta (seguridad del navegador)`);
        return;
      }
      this.setInputValue(element, value);
      return;
    }

    // Contenido editable (contenteditable, divs, spans)
    if (element.isContentEditable) {
      element.textContent = value;
      element.dispatchEvent(new Event("input", { bubbles: true }));
      element.dispatchEvent(new Event("change", { bubbles: true }));
      return;
    }

    // Último recurso: texto plano
    element.textContent = value;
  }

  // ------------------------------------------------------------------
  // Helpers de escritura
  // ------------------------------------------------------------------
  private selectRadio(firstRadio: HTMLInputElement, value: string): void {
    const normalized = this.normalize(value);
    const radios = Array.from(
      document.querySelectorAll<HTMLInputElement>(`input[name="${CSS.escape(firstRadio.name)}"]`)
    );

    // 1) Coincidencia por value
    let target = radios.find((radio) => this.normalize(radio.value) === normalized);

    // 2) Coincidencia por texto del label asociado
    target ??= radios.find((radio) => {
      const label = document.querySelector(`label[for="${CSS.escape(radio.id)}"]`)?.textContent ?? "";
      return this.normalize(label) === normalized || this.normalize(label).includes(normalized);
    });

    if (!target) {
      console.log(`[AutoApply] Radio "${firstRadio.name}": no se encontró opción para "${value}"`);
      return;
    }

    if (!target.checked) {
      target.checked = true;
      // click() + eventos para que React/Vue/Teamtailor reaccionen
      target.dispatchEvent(new MouseEvent("click", { bubbles: true }));
      target.dispatchEvent(new Event("input", { bubbles: true }));
      target.dispatchEvent(new Event("change", { bubbles: true }));
      console.log(`[AutoApply] Radio "${firstRadio.name}" = "${value}"`);
    }
  }

  private setCheckbox(checkbox: HTMLInputElement, value: string): void {
    const checked = /^(true|1|yes|si|sí|on|verdadero)$/i.test(this.normalize(value));
    if (checkbox.checked !== checked) {
      checkbox.checked = checked;
      checkbox.dispatchEvent(new MouseEvent("click", { bubbles: true }));
      checkbox.dispatchEvent(new Event("input", { bubbles: true }));
      checkbox.dispatchEvent(new Event("change", { bubbles: true }));
      console.log(`[AutoApply] Checkbox "${checkbox.name || checkbox.id}" = ${checked}`);
    }
  }

  private setSelectValue(select: HTMLSelectElement, value: string): void {
    const normalized = this.normalize(value);
    const options = Array.from(select.options);

    // Coincidencia exacta por value
    let option = options.find((opt) => this.normalize(opt.value) === normalized);

    // Coincidencia por texto visible
    option ??= options.find(
      (opt) =>
        this.normalize(opt.text) === normalized || this.normalize(opt.text).includes(normalized)
    );

    if (!option) {
      console.log(`[AutoApply] Select "${select.name}": no se encontró opción para "${value}"`);
      return;
    }

    if (select.value !== option.value) {
      select.value = option.value;
      select.dispatchEvent(new Event("input", { bubbles: true }));
      select.dispatchEvent(new Event("change", { bubbles: true }));
      console.log(`[AutoApply] Select "${select.name}" = "${option.text}" (${option.value})`);
    }
  }

  private setInputValue(element: HTMLInputElement | HTMLTextAreaElement, value: string): void {
    // Truco necesario para que React/Vue detecten el cambio programático
    const prototype = Object.getPrototypeOf(element);
    const descriptor = Object.getOwnPropertyDescriptor(prototype, "value");
    const nativeSetter = descriptor?.set;

    if (nativeSetter) {
      nativeSetter.call(element, value);
    } else {
      element.value = value;
    }

    element.dispatchEvent(new Event("input", { bubbles: true }));
    element.dispatchEvent(new Event("change", { bubbles: true }));
    console.log(`[AutoApply] Escrito en [name="${element.name}"]:`, value);
  }

  private normalize(text: string): string {
    return text.toLowerCase().replace(/\s+/g, " ").trim();
  }
}