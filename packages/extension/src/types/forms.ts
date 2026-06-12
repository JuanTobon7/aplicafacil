export interface FormField {
    label: string;
    name: string;
    type: string;
    required: boolean;
    placeholder?: string;
    fieldType: FieldType;
}

export interface JobForm {
    url: string;
    title: string;
    fields: FormField[];
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
    UNKNOWN = "UNKNOWN"
}