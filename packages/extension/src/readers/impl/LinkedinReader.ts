import { postRecommendations } from "../../api/recommendations";
import { RadioGroupProcessor } from "../../core/RadioGroupProcessor";
import { StandardInputProcessor } from "../../core/StandardInputProcessor";
import { FormField, JobForm } from "../../core/types/forms";
import { MetadataExtractorFactory } from "../MetadataExtractorFactory";
import { Reader } from "../base/Reader";


export class LinkedinReader implements Reader {
  private readonly standardProcessor = new StandardInputProcessor();
  private readonly radioProcessor = new RadioGroupProcessor();

  available(): boolean {
    const isLinkedin = location.hostname.includes("linkedin.com");
    const isJobPage = location.href.includes("/jobs/");
    const hasDialogContent = !!document.querySelector('[data-testid="dialog-content"]');
    const hasRoleDialog = !!document.querySelector('[role="dialog"]');
    const hasClassicModal = !!document.querySelector(
      '.jobs-easy-apply-modal, .artdeco-modal, [data-test-modal]'
    );
    
    const isAvailable = isLinkedin && isJobPage;
    const hasForm = hasDialogContent || hasRoleDialog || hasClassicModal || 
                    (document.querySelector('form') !== null);
    
    console.log("[LinkedinReader] available() check:", {
      isLinkedin,
      isJobPage,
      hasDialogContent,
      hasRoleDialog,
      hasClassicModal,
      result: isAvailable && hasForm,
    });
    
    return isAvailable && hasForm;
  }

  readContent(): JobForm {
    console.log("[LinkedinReader] readContent START");
    let metadata: Record<string, any> = {};
    try {
      const extractor = MetadataExtractorFactory.getExtractor("linkedin");
      console.log("[LinkedinReader] Extractor class:", extractor.constructor.name);
      metadata = extractor.extract();
      console.log("[LinkedinReader] Extracted metadata:", metadata);
    } catch (err) {
      console.error("[LinkedinReader] Error extracting metadata:", err);
    }

    const form = this.findEasyApplyForm();
    console.log("[LinkedinReader] EasyApply form found?", !!form);

    if (!form) {
      const result = {
        url: location.href,
        title: metadata?.title ?? "",
        fields: [],
        metadata,
      };
      console.log("[LinkedinReader] Returning without form:", result);
      return result;
    }

    const fields = this.extractFormFields(form);
    const result = {
      url: location.href,
      title: metadata?.title ?? "",
      fields,
      metadata,
    };
    console.log("[LinkedinReader] Returning with form and fields:", result);
    postRecommendations(result).then(recs => {
        console.log("[LinkedinReader] Recommendations received:", recs);
      }).catch(err => {
        console.error("[LinkedinReader] Error posting for recommendations:", err);
    });
    return result;
}

  private findEasyApplyForm(): HTMLFormElement | null {
    console.log("[LinkedinReader] Finding EasyApply form...");
    
    // Primero intenta encontrar el dialog moderno de LinkedIn
    const dialogContent = document.querySelector('[data-testid="dialog-content"]');
    if (dialogContent) {
      console.log("[LinkedinReader] Found dialog-content element");
      const form = dialogContent.querySelector('form');
      if (form) {
        console.log("[LinkedinReader] Found form in dialog-content");
        return form;
      }
      // Si no hay form explícito, busca el contenedor del formulario
      const formContainer = dialogContent as HTMLFormElement;
      if (formContainer) {
        console.log("[LinkedinReader] Using dialog-content as form container");
        return formContainer;
      }
    }

    // Busca por role="dialog"
    const roleDialog = document.querySelector('[role="dialog"]');
    if (roleDialog) {
      console.log("[LinkedinReader] Found role=dialog element");
      const form = roleDialog.querySelector('form');
      if (form) return form;
    }

    // Selectores clásicos de LinkedIn
    const modalSelectors = [
      '.jobs-easy-apply-modal',
      '.artdeco-modal',
      '[data-test-modal]',
      '.jobs-s-apply',
      '.modal-content',
    ];
    
    for (const selector of modalSelectors) {
      const modal = document.querySelector(selector);
      if (modal) {
        console.log("[LinkedinReader] Found modal with selector:", selector);
        const form = modal.querySelector('form');
        if (form) {
          console.log("[LinkedinReader] Found form in modal");
          return form;
        }
      }
    }

    // Si nada coincide, busca cualquier formulario visible en la página
    const forms = Array.from(document.querySelectorAll('form'));
    console.log("[LinkedinReader] Total forms found:", forms.length);
    
    const visibleForm = forms.find(f => {
      const isVisible = f.offsetParent !== null;
      const notSearchBox = !f.closest('.jobs-search-box');
      const isLarge = (f as any).querySelectorAll('input, textarea, select').length > 2;
      return isVisible && notSearchBox && isLarge;
    });
    
    if (visibleForm) {
      console.log("[LinkedinReader] Found visible form");
      return visibleForm;
    }

    console.log("[LinkedinReader] No form found");
    return null;
  }

  private extractFormFields(form: HTMLFormElement | HTMLElement): FormField[] {
    console.log("[LinkedinReader] Extracting form fields...");
    const fields: FormField[] = [];

    // Busca todos los inputs, textareas y selects dentro del formulario/contenedor
    const standardElements = form.querySelectorAll(
      'input:not([type="hidden"]):not([type="radio"]):not([type="checkbox"]), ' +
      'textarea:not([disabled]), ' +
      'select:not([disabled])'
    );
    
    console.log("[LinkedinReader] Found", standardElements.length, "standard elements");
    
    standardElements.forEach((el, index) => {
      console.log(`[LinkedinReader] Processing element ${index}:`, {
        tag: el.tagName,
        type: (el as any).type,
        name: (el as any).name || 'no-name',
        id: (el as any).id || 'no-id',
        visible: (el as HTMLElement).offsetParent !== null,
      });

      if (this.standardProcessor.canProcess(el)) {
        const field = this.standardProcessor.process(el);
        if (field) {
          console.log("[LinkedinReader] Added field:", field.name);
          fields.push(field);
        }
      }
    });

    // Busca radios y checkboxes agrupados en fieldsets
    const fieldsets = form.querySelectorAll('fieldset');
    console.log("[LinkedinReader] Found", fieldsets.length, "fieldsets");
    
    fieldsets.forEach(fieldset => {
      if (this.radioProcessor.canProcess(fieldset)) {
        const field = this.radioProcessor.process(fieldset);
        if (field && !fields.some(f => f.name === field.name)) {
          console.log("[LinkedinReader] Added radio/checkbox field:", field.name);
          fields.push(field);
        }
      }
    });

    console.log("[LinkedinReader] Total fields extracted:", fields.length, fields);
    return fields;
  }
}