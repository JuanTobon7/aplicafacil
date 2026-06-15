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

  private constructor() {
    this.detectors.push(new UrlChangeDetector());
    this.detectors.push(new DomChangeDetector());
  }

  static getInstance(): FormScanner {
    console.log("[AutoApply] FormScanner getInstance called");
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