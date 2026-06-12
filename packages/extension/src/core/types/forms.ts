export interface FormFieldOption {
    value: string;
    label: string;
}

export interface FormField {
    label: string;
    name: string;
    type: string;
    required: boolean;
    placeholder?: string;
    fieldType: FieldType;
    options?: FormFieldOption[];       // para radio, select, checkbox
    description?: string;              // texto de apoyo debajo del label
}

export interface JobForm {
    url: string;
    title: string;
    fields: FormField[];
    metadata: Record<string, any>; 
}

export enum FieldType {
    FIRST_NAME = "FIRST_NAME",
    LAST_NAME = "LAST_NAME",
    EMAIL = "EMAIL",
    PHONE = "PHONE",
    LINKEDIN = "LINKEDIN",
    PORTFOLIO = "PORTFOLIO",
    CV = "CV",
    COUNTRY = "COUNTRY",
    RADIO = "RADIO",
    CHECKBOX = "CHECKBOX",
    TEXTAREA = "TEXTAREA",
    UNKNOWN = "UNKNOWN"
}