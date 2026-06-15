import {
  IsString,
  IsOptional,
  IsInt,
  Min,
  Max,
  MaxLength,
} from 'class-validator';

export class CreateSkillDto {

  @IsString()
  @MaxLength(100)
  name!: string;

  @IsOptional()
  @IsString()
  @MaxLength(255)
  description?: string;

  @IsOptional()
  @IsInt()
  @Min(0)
  @Max(80)
  yearsOfExperience?: number;
}