import { IChangeDetector } from "./IchangeDetector";

export class ModalOpenDetector implements IChangeDetector {
  private callback?: () => void;
  private readonly clickHandler = (event: MouseEvent) => {
    const target = event.target as HTMLElement;

    // Detecta clics en botones de aplicar/postular (selectores CSS válidos)
    const trigger = target.closest(
      '[data-modal], [data-target*="modal"], .apply-button, ' +
      '[data-ui="apply-button"], ' +
      'button[aria-label*="Aplicar"], ' +
      'button[aria-label*="Apply"], ' +
      'button[aria-label*="Postular"], ' +
      '[data-test-modal-open]'
    );

    if (trigger) {
      console.log("[AutoApply] Modal trigger clicked:", trigger);
      this.scheduleRescan();
      return;
    }

    // Detecta clics en botones de avance de formularios multi-paso
    // (LinkedIn Easy Apply: "Siguiente", "Continuar", "Revisar", "Enviar", etc.)
    const stepButton = target.closest(
      'button, [role="button"]'
    );
    if (stepButton && this.isStepButton(stepButton)) {
      console.log("[AutoApply] Step button clicked:", stepButton);
      this.scheduleRescan();
    }
  };

  private mutationObserver?: MutationObserver;
  private lastDialogCount = 0;

  start(callback: () => void): void {
    this.callback = callback;

    // Click listener
    document.addEventListener('click', this.clickHandler);

    // MutationObserver para detectar cuando aparecen nuevos elementos dialog/modal
    this.mutationObserver = new MutationObserver((mutations) => {
      const dialogs = Array.from(document.querySelectorAll(
        '[data-testid="dialog-content"], [role="dialog"], .jobs-easy-apply-modal, .artdeco-modal'
      ));

      if (dialogs.length > this.lastDialogCount) {
        console.log("[AutoApply] New dialog detected via MutationObserver");
        this.lastDialogCount = dialogs.length;
        this.scheduleRescan();
        return;
      }

      // Reacciona si dentro de un dialog existente cambian los controles de
      // formulario (pasar de paso en Easy Apply). Se restringe a nodos DENTRO
      // de un dialog visible para no sobre-leer cambios por scroll.
      const controlsChanged = mutations.some(mutation =>
        [...mutation.addedNodes, ...mutation.removedNodes].some(node => {
          if (node.nodeType !== Node.ELEMENT_NODE) return false;
          const el = node as HTMLElement;
          if (!el.matches?.('input, textarea, select, fieldset')) return false;

          // El control debe estar dentro de un dialog visible
          return dialogs.some(dialog => this.isVisible(dialog) && dialog.contains(el));
        })
      );

      if (controlsChanged) {
        console.log("[AutoApply] Form controls changed inside dialog");
        this.scheduleRescan();
      }
    });

    this.mutationObserver.observe(document.body, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: ['data-testid', 'role', 'class'],
    });
  }

  stop(): void {
    document.removeEventListener('click', this.clickHandler);
    if (this.mutationObserver) {
      this.mutationObserver.disconnect();
    }
  }

  // Espera a que el framework renderice el siguiente paso antes de escanear
  private scheduleRescan(): void {
    setTimeout(() => this.callback?.(), 300);
    setTimeout(() => this.callback?.(), 600);
    setTimeout(() => this.callback?.(), 1000);
  }

  private isVisible(element: HTMLElement): boolean {
    if (element.hasAttribute('hidden')) return false;
    if (element.getAttribute('aria-hidden') === 'true') return false;
    if (element.offsetParent !== null) return true;
    return element.getClientRects().length > 0;
  }

  private isStepButton(element: Element): boolean {
    const text = (element.textContent ?? "").toLowerCase().trim();
    const aria = (element.getAttribute('aria-label') ?? "").toLowerCase();
    const isButton = element.matches('button, [role="button"]');

    // Botones de avance comunes en formularios de postulación
    const stepPatterns = [
      /\b(siguiente|continuar|next|continue|review|revisar|submit|enviar|finalizar|finish|done|listo)\b/,
    ];

    return isButton && (stepPatterns.some(p => p.test(text)) || stepPatterns.some(p => p.test(aria)));
  }
}