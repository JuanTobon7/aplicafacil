import { Recommendation } from "../../api/recommendations";
import { IWriter } from "../base/IWriter";

// ------------------------------------------------------------------
// Clase base: encapsula el "cómo" se escribe en un elemento del DOM.
// Los writers específicos por sitio solo necesitan sobreescribir
// available() y, si el sitio lo requiere, resolveElement().
// ------------------------------------------------------------------
export abstract class BaseWriter implements IWriter {
  abstract available(): boolean;

  writeRecommendations(recommendations: Recommendation[]): void {
    console.log("[AutoApply] Writing recommendations:", recommendations.length);

    recommendations.forEach((recommendation) => {
      const element = this.resolveElement(recommendation.id);

      if (!element) {
        console.log(`[AutoApply] No se encontró elemento con id "${recommendation.id}"`);
        return;
      }

      this.writeToElement(element, recommendation);
    });
  }

  // Por defecto busca por id directo. Los writers de sitios específicos
  // pueden sobreescribir esto si el id necesita transformarse (prefijos, etc.)
  protected resolveElement(id: string): HTMLElement | null {
    return document.getElementById(id);
  }

  protected writeToElement(element: HTMLElement, recommendation: Recommendation): void {
    const value = this.buildContent(recommendation);

    if (this.isInputLike(element)) {
      this.setInputValue(element as HTMLInputElement | HTMLTextAreaElement, value);
    } else if (element.isContentEditable) {
      element.textContent = value;
      element.dispatchEvent(new Event("input", { bubbles: true }));
    } else {
      element.textContent = value;
    }

    console.log(`[AutoApply] Escrito en #${recommendation.id}:`, value);
  }

  // Sobreescribible: qué texto exacto se escribe (título, descripción, ambos, etc.)
  protected buildContent(recommendation: Recommendation): string {
    return recommendation.description || recommendation.title;
  }

  private isInputLike(element: HTMLElement): element is HTMLInputElement | HTMLTextAreaElement {
    return element.tagName === "INPUT" || element.tagName === "TEXTAREA";
  }

  private setInputValue(element: HTMLInputElement | HTMLTextAreaElement, value: string): void {
    const prototype = Object.getPrototypeOf(element);
    const descriptor = Object.getOwnPropertyDescriptor(prototype, "value");
    const nativeSetter = descriptor?.set;

    // Truco necesario para que React/Vue detecten el cambio programático
    if (nativeSetter) {
      nativeSetter.call(element, value);
    } else {
      element.value = value;
    }

    element.dispatchEvent(new Event("input", { bubbles: true }));
    element.dispatchEvent(new Event("change", { bubbles: true }));
  }
}