import { FieldOptionDto } from "./field.option.dto.js";

export class FieldDto {
  label!: string;
  description?: string;
  name!: string;                // el name del input en el DOM (lo necesita la extensión para inyectar)
  type!: string;                // 'text' | 'select' | 'textarea' | 'tel' | etc.
  required!: boolean;
  placeholder?: string;
  fieldType?: string;           // 'UNKNOWN' | otros valores de LinkedIn
  options?: FieldOptionDto[];   // solo en selects
}