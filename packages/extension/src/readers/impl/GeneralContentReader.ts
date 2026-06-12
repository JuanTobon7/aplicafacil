import { FieldProcessor } from "../../core/base/FieldFieldProcessor";
import { FormField, JobForm } from "../../core/types/forms";
import { Reader } from "../base/Reader";
import { StandardInputProcessor } from "../../core/StandardInputProcessor";
import { RadioGroupProcessor } from "../../core/RadioGroupProcessor";
import { TeamtailorChoiceProcessor } from "../../core/TeamtailorChoiceProcessor";
import { FieldTypeDetector } from "../../core/types/InputType";
import { MetadataExtractorFactory } from "../MetadataExtractorFactory";

export class GeneralContentReader implements Reader {
  private readonly processors: FieldProcessor[];
  private readonly typeDetector: FieldTypeDetector
  ;

  constructor() {
    this.processors = [
      new StandardInputProcessor(),
      new RadioGroupProcessor(),
      new TeamtailorChoiceProcessor()
      // Se pueden agregar más sin modificar el reader
    ];
    this.typeDetector = new FieldTypeDetector();
  }

  readContent(): JobForm {
    const form = document.querySelector<HTMLFormElement>('form');
    if (!form) return { url: window.location.href, title: document.title, fields: [], metadata: {} };

    const fields: FormField[] = [];
    
    const extractor = MetadataExtractorFactory.getExtractor("general");
          console.log("[GeneralContentReader] Extractor class:", extractor.constructor.name);
    const metadata = extractor.extract();
    // Recorrer elementos que pueden ser manejados por algún procesador
    const candidateElements = this.getAllCandidateElements(form);
    
    for (const element of candidateElements) {
      for (const processor of this.processors) {
        if (processor.canProcess(element, form)) {
          const field = processor.process(element, form);
          if (field && !this.isDuplicate(fields, field)) {
            // Asignar el tipo semántico
            field.fieldType = this.typeDetector.detect(field);
            fields.push(field);
          }
          break; // un solo procesador por elemento
        }
      }
    }

    return {
      url: window.location.href,
      title: metadata?.title ?? document.title,
      fields,
      metadata
    };
  }

  available(): boolean {
    const form = document.querySelector<HTMLFormElement>('form');
    if (!form) return false;
    const visibleFields = Array.from(form.querySelectorAll('input:not([type="hidden"]), textarea, select'))
      .filter(el => (el as HTMLElement).offsetParent !== null && !el.hasAttribute('disabled'));
    return visibleFields.length >= 3;
  }

  private getAllCandidateElements(form: HTMLFormElement): Element[] {
    // Todos los elementos que podrían ser procesados
    const selectors = [
      'input:not([type="hidden"]):not([type="radio"]):not([type="checkbox"])',
      'textarea', 'select', 'fieldset', '[data-controller*="forms--inputs--choice"]'
    ];
    return Array.from(form.querySelectorAll(selectors.join(',')));
  }

  private isDuplicate(fields: FormField[], newField: FormField): boolean {
    return fields.some(f => f.name === newField.name && f.type === newField.type);
  }
}