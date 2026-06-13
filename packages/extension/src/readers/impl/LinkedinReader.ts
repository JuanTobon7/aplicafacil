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
    return (
      location.hostname.includes("linkedin.com") &&
      location.href.includes("/jobs/")
    );
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
    const modalSelectors = [
      '.jobs-easy-apply-modal',
      '.artdeco-modal',
      '[data-test-modal]',
      '.jobs-s-apply',
    ];
    for (const selector of modalSelectors) {
      const modal = document.querySelector(selector);
      if (modal) {
        const form = modal.querySelector('form');
        if (form) return form;
      }
    }
    const forms = Array.from(document.querySelectorAll('form'));
    return forms.find(f => f.offsetParent !== null && !f.closest('.jobs-search-box')) || null;
  }

  private extractFormFields(form: HTMLFormElement): FormField[] {
    const fields: FormField[] = [];

    const standardElements = form.querySelectorAll(
      'input:not([type="hidden"]):not([type="radio"]):not([type="checkbox"]), textarea, select'
    );
    standardElements.forEach(el => {
      if (this.standardProcessor.canProcess(el)) {
        const field = this.standardProcessor.process(el);
        if (field) fields.push(field);
      }
    });

    const fieldsets = form.querySelectorAll('fieldset');
    fieldsets.forEach(fieldset => {
      if (this.radioProcessor.canProcess(fieldset)) {
        const field = this.radioProcessor.process(fieldset);
        if (field && !fields.some(f => f.name === field.name)) {
          fields.push(field);
        }
      }
    });

    return fields;
  }
}