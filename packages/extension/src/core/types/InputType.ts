import { FieldType, FormField } from "./forms";

type TypeRule = {
  patterns: RegExp[];
  fieldType: FieldType;
};

export class FieldTypeDetector {
  private readonly rules: TypeRule[] = [
    { patterns: [/email/i, /correo/i], fieldType: FieldType.EMAIL },
    { patterns: [/phone/i, /telefono/i, /teléfono/i, /móvil/i, /movil/i], fieldType: FieldType.PHONE },
    { patterns: [/linkedin/i], fieldType: FieldType.LINKEDIN },
    { patterns: [/portfolio/i], fieldType: FieldType.PORTFOLIO },
    { patterns: [/resume/i, /curriculum/i, /currículum/i, /cv/i], fieldType: FieldType.CV },
    { patterns: [/first name/i, /nombre/i, /first_name/i, /firstname/i], fieldType: FieldType.FIRST_NAME },
    { patterns: [/last name/i, /apellido/i, /last_name/i, /lastname/i], fieldType: FieldType.LAST_NAME },
    { patterns: [/country/i, /país/i, /pais/i], fieldType: FieldType.COUNTRY }
  ];

  detect(field: Partial<FormField>): FieldType {
    const text = `${field.label} ${field.name}`.toLowerCase();
    for (const rule of this.rules) {
      if (rule.patterns.some(p => p.test(text))) {
        return rule.fieldType;
      }
    }
    
    // Fallback según tipo de input nativo
    if (field.type === 'file') return FieldType.CV;
    if (field.type === 'radio') return FieldType.RADIO;
    if (field.type === 'checkbox') return FieldType.CHECKBOX;
    if (field.type === 'textarea') return FieldType.TEXTAREA;
    
    return FieldType.UNKNOWN;
  }
}