import { Type } from 'class-transformer';
import {
  IsArray,
  IsBoolean,
  IsOptional,
  IsString,
  ValidateNested,
} from 'class-validator';
import { FieldOptionDto } from './FieldOptionDto';
 
export class FieldDto {
  @IsString()
  label!: string;
 
  @IsString()
  @IsOptional()
  description?: string;

  @IsString()
  name!: string;                // el name del input en el DOM (lo necesita la extensión para inyectar)
 
  @IsString()
  type!: string;                // 'text' | 'select' | 'textarea' | 'tel' | etc.
 
  @IsBoolean()
  required!: boolean;
 
  @IsOptional()
  @IsString()
  placeholder?: string;
 
  @IsOptional()
  @IsString()
  fieldType?: string;           // 'UNKNOWN' | otros valores de LinkedIn
 
  @IsOptional()
  @IsArray()
  @ValidateNested({ each: true })
  @Type(() => FieldOptionDto)
  options?: FieldOptionDto[];   // solo en selects
}