import { IChangeDetector } from "./IchangeDetector";

export class ModalOpenDetector implements IChangeDetector {
  private callback?: () => void;
  private clickHandler = (event: MouseEvent) => {
    const target = event.target as HTMLElement;
    
    // Detecta clics en botones de aplicar/postular
    const trigger = target.closest(
      '[data-modal], [data-target*="modal"], .apply-button, ' +
      '[data-ui="apply-button"], ' +
      'button[aria-label*="Aplicar"], ' +
      'button[aria-label*="Apply"], ' +
      'button:contains("Apply"), ' +
      'button:contains("Postular"), ' +
      'button:contains("Easy Apply"), ' +
      '[data-test-modal-open]'
    );
    
    if (trigger) {
      console.log("[AutoApply] Modal trigger clicked:", trigger);
      // Espera a que el modal/dialog se renderice completamente
      setTimeout(() => this.callback?.(), 300);
      setTimeout(() => this.callback?.(), 600);
      setTimeout(() => this.callback?.(), 1000);
    }
  };

  // Detector de MutationObserver para captar cuando aparecen modales/dialogs
  private mutationObserver?: MutationObserver;
  private lastDialogCount = 0;

  start(callback: () => void): void {
    this.callback = callback;
    
    // Click listener
    document.addEventListener('click', this.clickHandler);
    
    // MutationObserver para detectar cuando aparecen nuevos elementos dialog/modal
    this.mutationObserver = new MutationObserver((mutations) => {
      // Busca por data-testid="dialog-content" o elementos con role="dialog"
      const dialogs = document.querySelectorAll(
        '[data-testid="dialog-content"], [role="dialog"], .jobs-easy-apply-modal, .artdeco-modal'
      );
      
      if (dialogs.length > this.lastDialogCount) {
        console.log("[AutoApply] New dialog detected via MutationObserver");
        this.lastDialogCount = dialogs.length;
        setTimeout(() => this.callback?.(), 200);
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
}