import {
  IsString,
  IsOptional,
  IsDateString,
  MaxLength,
} from 'class-validator';

export class CreateEducationDto {

  @IsString()
  @MaxLength(100)
  institutionName!: string;

  @IsOptional()
  @IsString()
  @MaxLength(250)
  description?: string;

  @IsDateString()
  startDate!: string;

  @IsOptional()
  @IsDateString()
  endDate?: string;
}