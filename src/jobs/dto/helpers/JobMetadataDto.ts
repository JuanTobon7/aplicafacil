import { Type } from 'class-transformer';
import {
  IsOptional,
  IsString,
  ValidateNested,
} from 'class-validator';
import { SkillsMatchDto } from './SkillMatchDto';
 
export class JobMetadataDto {
  @IsString()
  title!: string;               // "Desarrollador Java"
 
  @IsString()
  company!: string;             // "Amaris Consulting"
 
  @IsString()
  location!: string;            // "Bogotá, Colombia · ..."
 
  @IsOptional()
  @IsString()
  postedDate?: string;
 
  @IsString()
  description!: string;         // descripción completa — el oro para el LLM
 
  @IsOptional()
  @IsString()
  workplaceType?: string;       // 'hybrid' | 'remote' | 'onsite'
 
  @IsOptional()
  @IsString()
  employmentType?: string;      // 'full-time' | 'part-time'
 
  @IsOptional()
  @ValidateNested()
  @Type(() => SkillsMatchDto)
  skillsMatch?: SkillsMatchDto;
 
  @IsOptional()
  @IsString()
  url?: string;
}