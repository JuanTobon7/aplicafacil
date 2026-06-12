import { JobForm } from "./core/types/forms";
import { ReaderFactory } from "./readers/FactoryReader";

// ------------------------------------------------------------------
// Interfaces y detectores de cambio (Strategy)
// ------------------------------------------------------------------
interface IChangeDetector {
  start(callback: () => void): void;
  stop(): void;
}

// Detecta cambios de URL en SPAs (con setInterval)
class UrlChangeDetector implements IChangeDetector {
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

// Detecta cambios en el DOM que puedan añadir formularios o mostrar modales
class DomChangeDetector implements IChangeDetector {
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

// Detecta clics en botones que suelen abrir modales (aunque el modal ya exista oculto)
class ModalOpenDetector implements IChangeDetector {
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

// ------------------------------------------------------------------
// Orquestador principal (Singleton)
// ------------------------------------------------------------------
class FormScanner {
  private static instance: FormScanner;
  private detectors: IChangeDetector[] = [];
  private isScanning = false;

  private constructor() {
    this.detectors.push(new UrlChangeDetector());
    this.detectors.push(new DomChangeDetector());
    this.detectors.push(new ModalOpenDetector());
  }

  static getInstance(): FormScanner {
    if (!FormScanner.instance) FormScanner.instance = new FormScanner();
    return FormScanner.instance;
  }

  start(): void {
    this.detectors.forEach(d => d.start(() => this.scanAndNotify()));
    this.scanAndNotify(); // escaneo inicial inmediato
  }

  stop(): void {
    this.detectors.forEach(d => d.stop());
  }

  // Escaneo bajo demanda (para el popup)
  scanOnDemand(): JobForm | null {
    return this.scanCurrentPage();
  }

  private scanAndNotify(): void {
    if (this.isScanning) return;
    this.isScanning = true;

    // Escaneamos sin setTimeout para que sea inmediato como en la versión antigua
    const form = this.scanCurrentPage();
    if (form && form.fields.length) {
      chrome.runtime.sendMessage({ type: "FORM_DETECTED", form });
    }
    this.isScanning = false;
  }

  private scanCurrentPage(): JobForm | null {
    console.log("[AutoApply] Scanning current page:", window.location.hostname);
    const reader = ReaderFactory.getReader(window.location.hostname);
    console.log("[AutoApply] Reader:", reader.constructor.name);

    if (!reader.available()) {
      console.log("[AutoApply] Reader not available");
      return null;
    }

    const jobForm = reader.readContent();
    if (!jobForm.fields.length) {
      console.log("[AutoApply] No fields found");
      return null;
    }

    console.log("[AutoApply] Form detected:", jobForm);
    return jobForm;
  }
}

// ------------------------------------------------------------------
// Inicialización y comunicación con el background/popup
// ------------------------------------------------------------------
const scanner = FormScanner.getInstance();

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  console.log("[AutoApply] Message received:", message);
  if (message.type === "READ_FORM") {
    const form = scanner.scanOnDemand();
    sendResponse(form ? { success: true, form } : { success: false, message: "No form fields detected." });
    return true;
  }
});

window.addEventListener("load", () => {
  console.log("[AutoApply] Page loaded, starting scanner");
  scanner.start();
});