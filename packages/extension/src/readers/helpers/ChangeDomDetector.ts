import { IChangeDetector } from "./IchangeDetector";

export class DomChangeDetector implements IChangeDetector {
  private observer?: MutationObserver;
  private timer?: number;

  start(callback: () => void): void {
    this.observer = new MutationObserver((mutations) => {
      clearTimeout(this.timer);

      // Verifica si alguna mutación es relevante (dialog, modal, formulario)
      const isRelevant = mutations.some(mutation => {
        // Si se agregaron nodos
        if (mutation.type === 'childList') {
          return Array.from(mutation.addedNodes).some(node => {
            if (node.nodeType === Node.ELEMENT_NODE) {
              const el = node as HTMLElement;
              return (
                el.tagName === 'FORM' ||
                el.tagName === 'DIALOG' ||
                el.getAttribute('data-testid')?.includes('dialog') ||
                el.getAttribute('role') === 'dialog' ||
                el.className.includes('modal') ||
                el.className.includes('dialog')
              );
            }
            return false;
          });
        }
        return false;
      });

      if (isRelevant) {
        console.log("[DomChangeDetector] Relevant mutation detected");
        this.timer = window.setTimeout(() => callback(), 300);
      } else {
        // Debounce sin cambios relevantes
        this.timer = window.setTimeout(() => {
          const hasFormElements = document.querySelector(
            'form, input:visible, textarea:visible, select:visible, [role="dialog"], [data-testid*="dialog"]'
          );

          if (hasFormElements) {
            console.log("[DomChangeDetector] Form elements detected");
            callback();
          }
        }, 500);
      }
    });

    this.observer.observe(document.body, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: ['data-testid', 'role', 'class'],
    });
  }

  stop(): void {
    clearTimeout(this.timer);
    this.observer?.disconnect();
  }
}