import { IsString } from 'class-validator';
 
export class FieldOptionDto {
  @IsString()
  value!: string;
 
  @IsString()
  label!: string;
}