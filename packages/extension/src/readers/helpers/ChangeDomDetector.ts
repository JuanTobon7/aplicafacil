import { IChangeDetector } from "./IchangeDetector";

// Selectores de "contenedor de formulario" (dialog, modal, form)
const FORM_CONTAINER_SELECTOR = 'form, [role="dialog"], [data-testid*="dialog"], [data-test-modal], .jobs-easy-apply-modal, .artdeco-modal';
// Controles de formulario que indican que un paso cambió
const FORM_CONTROL_SELECTOR = 'input, textarea, select, fieldset';

export class DomChangeDetector implements IChangeDetector {
  private observer?: MutationObserver;
  private timer?: number;

  start(callback: () => void): void {
    this.observer = new MutationObserver((mutations) => {
      clearTimeout(this.timer);

      const isRelevant = mutations.some(mutation => this.isRelevantMutation(mutation));

      if (isRelevant) {
        console.log("[DomChangeDetector] Relevant mutation detected");
        // Debounce corto: espera a que el framework termine de renderizar el paso
        this.timer = window.setTimeout(() => callback(), 250);
      }
    });

    this.observer.observe(document.body, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: ['data-testid', 'role', 'class', 'name', 'id', 'hidden', 'disabled', 'aria-hidden'],
    });
  }

  stop(): void {
    clearTimeout(this.timer);
    this.observer?.disconnect();
  }

  /**
   * Determina si una mutación puede implicar un cambio en el formulario.
   *
   * Para evitar "sobre-leer" mutaciones irrelevantes (scroll que carga
   * contenido con inputs fuera del formulario, animaciones, etc.), solo se
   * consideran los cambios dentro del contenedor de formulario visible
   * (dialog/modal/form). Esto cubre el caso de LinkedIn Easy Apply al pasar
   * de paso: se reemplazan los inputs dentro del mismo dialog.
   */
  private isRelevantMutation(mutation: MutationRecord): boolean {
    const container = this.getVisibleFormContainer();
    if (!container) return false;

    if (mutation.type === 'childList') {
      const nodes = [...mutation.addedNodes, ...mutation.removedNodes];

      return nodes.some(node => {
        if (node.nodeType !== Node.ELEMENT_NODE) return false;
        const el = node as HTMLElement;

        // El nodo debe estar dentro del contenedor visible (o ser el contenedor mismo)
        const insideContainer = container.contains(el) || el.contains(container);
        if (!insideContainer) return false;

        // Nodo que es un control o un contenedor de formulario
        if (el.matches?.(`${FORM_CONTAINER_SELECTOR}, ${FORM_CONTROL_SELECTOR}`)) return true;

        // Contenedor genérico que trae controles dentro (pasos de Easy Apply)
        return el.querySelector?.(FORM_CONTROL_SELECTOR) !== null;
      });
    }

    if (mutation.type === 'attributes') {
      const target = mutation.target as HTMLElement;

      // Cambios de visibilidad del contenedor en sí (abrir/cerrar, mostrar paso)
      if (target === container) return true;

      // Cambios sobre controles dentro del contenedor
      if (container.contains(target) && target.matches?.(FORM_CONTROL_SELECTOR)) return true;

      return false;
    }

    return false;
  }

  private getVisibleFormContainer(): HTMLElement | null {
    const candidates = document.querySelectorAll<HTMLElement>(FORM_CONTAINER_SELECTOR);
    for (const candidate of candidates) {
      if (this.isVisible(candidate)) return candidate;
    }
    return null;
  }

  private isVisible(element: HTMLElement): boolean {
    if (element.hasAttribute('hidden')) return false;
    if (element.getAttribute('aria-hidden') === 'true') return false;
    // offsetParent es null cuando el elemento (o un ancestro) está oculto
    if (element.offsetParent !== null) return true;
    // Fallback para elementos fijos/posicionados que reportan offsetParent null
    return element.getClientRects().length > 0;
  }
}