import { Recommendation } from "../api/recommendations";
import { postRecommendations } from "../api/recommendations";
import { FormScanner } from "../readers/helpers/FormScanner";
import { WriterFactory } from "../writers/FactoryWriter";
import { JobForm } from "./types/forms";

// ------------------------------------------------------------------
// Orquestador de auto-aplicación paso a paso en LinkedIn.
//
// Flujo:
//   1. Hacer clic en el botón "Solicitar" / "Apply" / "Postular"
//   2. Esperar a que aparezca el formulario Easy Apply
//   3. Completar los campos con las recomendaciones del servidor
//   4. Navegar por los pasos (Siguiente → Revisar → Enviar)
//   5. Confirmar la aplicación
//   6. Devolver el resultado
// ------------------------------------------------------------------

const STEP_BUTTON_SELECTORS = [
  'button[aria-label*="Siguiente"]',
  'button[aria-label*="Next"]',
  'button[aria-label*="Continuar"]',
  'button[aria-label*="Continue"]',
  'button[aria-label*="Revisar"]',
  'button[aria-label*="Review"]',
  'button[aria-label*="Enviar"]',
  'button[aria-label*="Submit"]',
  'button[aria-label*="Aplicar"]',
  'button[aria-label*="Apply"]',
  'button[aria-label*="Postular"]',
  'button[aria-label*="Solicitar"]',
  'button[aria-label*="Enviar solicitud"]',
  'button[aria-label*="Submit application"]',
  'button[type="submit"]',
];

const APPLY_BUTTON_SELECTORS = [
  'button[aria-label*="Solicitar"]',
  'button[aria-label*="Apply"]',
  'button[aria-label*="Postular"]',
  'button[aria-label*="Aplicar"]',
  'button[aria-label*="Easy Apply"]',
  'button[aria-label*="Solicitud sencilla"]',
  '.jobs-apply-button',
  '.jobs-s-apply button',
  'button[data-control-name="apply"]',
  'button[data-easy-apply]',
];

const CLOSE_BUTTON_SELECTORS = [
  'button[aria-label*="Cerrar"]',
  'button[aria-label*="Close"]',
  'button[aria-label*="Descartar"]',
  'button[aria-label*="Dismiss"]',
  '.artdeco-modal__dismiss',
  'button[data-test-modal-close]',
];

export interface AutoApplyResult {
  success: boolean;
  message: string;
  stepsCompleted: number;
}

export class LinkedInAutoApply {
  private readonly scanner = FormScanner.getInstance();
  private maxSteps = 10;

  /**
   * Ejecuta el flujo completo de auto-aplicación en la página actual.
   */
  async run(): Promise<AutoApplyResult> {
    console.log("[LinkedInAutoApply] Iniciando auto-aplicación paso a paso");

    // 1) Hacer clic en el botón de aplicar
    const applyClicked = this.clickApplyButton();
    if (!applyClicked) {
      console.log("[LinkedInAutoApply] No se encontró botón de aplicar");
      return { success: false, message: "No se encontró el botón de aplicar", stepsCompleted: 0 };
    }

    // 2) Esperar a que aparezca el formulario Easy Apply
    const formReady = await this.waitForForm();
    if (!formReady) {
      console.log("[LinkedInAutoApply] No apareció el formulario Easy Apply");
      return { success: false, message: "No apareció el formulario Easy Apply", stepsCompleted: 1 };
    }

    // 3) Completar los campos del formulario
    const form = this.scanner.scanOnDemand();
    if (!form?.fields?.length) {
      console.log("[LinkedInAutoApply] Formulario sin campos detectables");
      return { success: false, message: "Formulario sin campos detectables", stepsCompleted: 1 };
    }

    const recommendations = await postRecommendations(form);
    if (!recommendations.length) {
      console.log("[LinkedInAutoApply] Sin recomendaciones del servidor");
      return { success: false, message: "Sin recomendaciones del servidor", stepsCompleted: 1 };
    }

    this.writeRecommendations(recommendations);

    // 4) Navegar por los pasos del formulario
    let stepsCompleted = 1;
    for (let i = 0; i < this.maxSteps; i++) {
      const advanced = await this.advanceStep();
      if (!advanced) break;
      stepsCompleted++;

      // Después de avanzar, completar los campos del nuevo paso
      await this.waitForForm();
      const nextForm = this.scanner.scanOnDemand();
      if (nextForm?.fields?.length) {
        const nextRecs = await postRecommendations(nextForm);
        if (nextRecs.length) {
          this.writeRecommendations(nextRecs);
        }
      }

      // Si ya no hay formulario, la aplicación se completó
      if (!this.isFormPresent()) {
        console.log("[LinkedInAutoApply] Formulario cerrado, aplicación completada");
        return { success: true, message: "Aplicación completada", stepsCompleted };
      }
    }

    // 5) Verificar si la aplicación se completó
    const applied = await this.confirmApplication();
    if (applied) {
      return { success: true, message: "Aplicación completada", stepsCompleted };
    }

    return { success: false, message: "No se pudo completar la aplicación", stepsCompleted };
  }

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------

  private clickApplyButton(): boolean {
    for (const selector of APPLY_BUTTON_SELECTORS) {
      const button = document.querySelector<HTMLElement>(selector);
      if (button && this.isVisible(button)) {
        console.log("[LinkedInAutoApply] Clic en botón de aplicar:", selector);
        button.click();
        return true;
      }
    }
    return false;
  }

  private async waitForForm(timeoutMs = 10000): Promise<boolean> {
    const start = Date.now();
    while (Date.now() - start < timeoutMs) {
      if (this.isFormPresent()) return true;
      await this.sleep(500);
    }
    return false;
  }

  private isFormPresent(): boolean {
    return !!document.querySelector(
      '[data-testid="dialog-content"], [role="dialog"], .jobs-easy-apply-modal, .artdeco-modal, form'
    );
  }

  private async advanceStep(): Promise<boolean> {
    // Buscar botón de siguiente/revisar/enviar
    for (const selector of STEP_BUTTON_SELECTORS) {
      const button = document.querySelector<HTMLElement>(selector);
      if (button && this.isVisible(button)) {
        console.log("[LinkedInAutoApply] Avanzando paso con:", selector);
        button.click();
        await this.sleep(1500);
        return true;
      }
    }
    return false;
  }

  private async confirmApplication(): Promise<boolean> {
    // Esperar a que aparezca la confirmación de aplicación exitosa
    const successSelectors = [
      '.artdeco-inline-feedback--success',
      '[data-test-easy-apply-success]',
      '.jobs-easy-apply-success',
      'text=Application submitted',
      'text=Solicitud enviada',
    ];

    const start = Date.now();
    while (Date.now() - start < 8000) {
      for (const selector of successSelectors) {
        if (document.querySelector(selector)) {
          return true;
        }
      }
      // También verificar si el modal se cerró (aplicación completada)
      if (!this.isFormPresent()) {
        return true;
      }
      await this.sleep(500);
    }
    return false;
  }

  private writeRecommendations(recommendations: Recommendation[]): void {
    const writer = WriterFactory.getWriter(window.location.hostname);
    if (writer.available()) {
      writer.writeRecommendations(recommendations);
    }
  }

  private isVisible(el: HTMLElement): boolean {
    return el.offsetParent !== null && el.getClientRects().length > 0;
  }

  private sleep(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }
}
