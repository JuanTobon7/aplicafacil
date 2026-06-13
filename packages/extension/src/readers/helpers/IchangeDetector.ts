
// ------------------------------------------------------------------
// Interfaces y detectores de cambio (Strategy)
// ------------------------------------------------------------------
export interface IChangeDetector {
  start(callback: () => void): void;
  stop(): void;
}

// Detecta cambios de URL en SPAs (con setInterval)
export class UrlChangeDetector implements IChangeDetector {
  private lastUrl = "";
  private intervalId?: number;
  private callback?: () => void;

  start(callback: () => void): void {
    this.callback = callback;
    this.lastUrl = location.href;
    this.intervalId = window.setInterval(() => {
      if (this.lastUrl !== location.href) {
        this.lastUrl = location.href;
        console.log("[AutoApply] URL changed to:", location.href);
        callback();
      }
    }, 1000);
  }

  stop(): void {
    if (this.intervalId) clearInterval(this.intervalId);
  }
}