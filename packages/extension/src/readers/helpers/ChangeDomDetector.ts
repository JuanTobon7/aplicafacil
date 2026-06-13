import { IChangeDetector } from "./IchangeDetector";

export class DomChangeDetector implements IChangeDetector {
  private observer?: MutationObserver;
  private callback?: () => void;

  start(callback: () => void): void {
    this.callback = callback;
    this.observer = new MutationObserver(() => {
      // Comprobamos si hay algún formulario, campo o modal en el DOM actual
      const hasFormElements = document.querySelector('form, input, textarea, select, [role="dialog"], .modal, .dialog');
      if (hasFormElements) {
        console.log("[AutoApply] DOM change detected: form elements or modal found");
        callback();
      }
    });
    this.observer.observe(document.documentElement, { childList: true, subtree: true, attributes: true });
  }

  stop(): void {
    this.observer?.disconnect();
  }
}