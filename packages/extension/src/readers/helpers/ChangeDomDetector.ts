import { IChangeDetector } from "./IchangeDetector";

export class DomChangeDetector implements IChangeDetector {
  private observer?: MutationObserver;
  private timer?: number;

  start(callback: () => void): void {
    this.observer = new MutationObserver(() => {

      clearTimeout(this.timer);

      this.timer = window.setTimeout(() => {

        const hasFormElements = document.querySelector(
          'form, input, textarea, select, [role="dialog"]'
        );

        if (hasFormElements) {
          callback();
        }

      }, 500);

    });

    this.observer.observe(document.body, {
      childList: true,
      subtree: true,
    });
  }

  stop(): void {
    this.observer?.disconnect();
  }
}