import { IChangeDetector } from "./IchangeDetector";

export class ModalOpenDetector implements IChangeDetector {
  private callback?: () => void;
  private clickHandler = (event: MouseEvent) => {
    const target = event.target as HTMLElement;
    // Selectores comunes para botones de aplicar o abrir modal
    const trigger = target.closest('[data-modal], [data-target*="modal"], .apply-button, [data-ui="apply-button"], button:contains("Apply"), button:contains("Postular")');
    if (trigger) {
      console.log("[AutoApply] Modal trigger clicked:", trigger);
      setTimeout(() => this.callback?.(), 400); // espera a que el modal se renderice
    }
  };

  start(callback: () => void): void {
    this.callback = callback;
    document.addEventListener('click', this.clickHandler);
  }

  stop(): void {
    document.removeEventListener('click', this.clickHandler);
  }
}