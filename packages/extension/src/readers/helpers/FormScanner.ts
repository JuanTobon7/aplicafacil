// ------------------------------------------------------------------
// Orquestador principal (Singleton)

import { ReaderFactory } from "../FactoryReader";
import { DomChangeDetector } from "./ChangeDomDetector";
import { ModalOpenDetector } from "./ModalOpenDetector";
import { JobForm } from "../../core/types/forms";
import { IChangeDetector, UrlChangeDetector } from "./IchangeDetector";

// ------------------------------------------------------------------
export class FormScanner {
  private static instance: FormScanner;
  private readonly detectors: IChangeDetector[] = [];
  private isScanning = false;
  private lastFormHash = "";
  private scanDebounceTimer?: number;

  private constructor() {
    this.detectors.push(new UrlChangeDetector());
    this.detectors.push(new DomChangeDetector());
    this.detectors.push(new ModalOpenDetector());
  }

  static getInstance(): FormScanner {
    console.log("[AutoApply] FormScanner getInstance called");
    if (!FormScanner.instance) FormScanner.instance = new FormScanner();
    return FormScanner.instance;
  }

  start(): void {
    console.log("[AutoApply] FormScanner starting...");
    this.detectors.forEach(d => d.start(() => this.onDetectionTriggered()));
    this.scanAndNotify(); // escaneo inicial inmediato
  }

  stop(): void {
    console.log("[AutoApply] FormScanner stopping...");
    this.detectors.forEach(d => d.stop());
    clearTimeout(this.scanDebounceTimer);
  }

  // Escaneo bajo demanda (para el popup)
  scanOnDemand(): JobForm | null {
    console.log("[AutoApply] scanOnDemand called");
    return this.scanCurrentPage();
  }

  private onDetectionTriggered(): void {
    console.log("[AutoApply] Detection triggered, debouncing scan...");
    clearTimeout(this.scanDebounceTimer);
    this.scanDebounceTimer = window.setTimeout(() => {
      this.scanAndNotify();
    }, 200);
  }

  private scanAndNotify(): void {
    if (this.isScanning) {
      console.log("[AutoApply] Already scanning, skipping");
      return;
    }
    
    this.isScanning = true;

    try {
      const form = this.scanCurrentPage();
      
      if (form && form.fields.length) {
        // Calcula hash simple para evitar duplicados
        const formHash = JSON.stringify({ 
          url: form.url, 
          fieldsCount: form.fields.length,
          fieldNames: form.fields.map(f => f.name).join(',')
        });

        if (formHash !== this.lastFormHash) {
          console.log("[AutoApply] Form changed, sending FORM_DETECTED");
          this.lastFormHash = formHash;
          chrome.runtime.sendMessage({ type: "FORM_DETECTED", form })
            .catch(err => console.error("[AutoApply] Error sending message:", err));
        } else {
          console.log("[AutoApply] Form unchanged, not sending duplicate");
        }
      } else {
        console.log("[AutoApply] No form or no fields detected");
      }
    } finally {
      this.isScanning = false;
    }
  }

  private scanCurrentPage(): JobForm | null {
    console.log("[AutoApply] Scanning current page:", window.location.hostname);
    const reader = ReaderFactory.getReader(window.location.hostname);
    console.log("[AutoApply] Selected reader:", reader.constructor.name);

    if (!reader.available()) {
      console.log("[AutoApply] Reader not available for this page");
      return null;
    }

    console.log("[AutoApply] Reader available, reading content...");
    const jobForm = reader.readContent();
    
    console.log("[AutoApply] Scan complete:", {
      hasFields: jobForm.fields.length > 0,
      fieldCount: jobForm.fields.length,
      fieldsNames: jobForm.fields.map(f => f.name),
    });

    return jobForm;
  }
}