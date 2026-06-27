import { SkillsMatchDto } from "./skill.match.dto.js";

export class JobMetadataDto {
  title!: string;               // "Desarrollador Java"
  company!: string;             // "Amaris Consulting"
  location!: string;            // "Bogotá, Colombia · ..."
  postedDate?: string;
  description!: string;         // descripción completa — el oro para el LLM
  workplaceType?: string;       // 'hybrid' | 'remote' | 'onsite'
  employmentType?: string;      // 'full-time' | 'part-time'
  skillsMatch?: SkillsMatchDto;
  url?: string;
}