import { EducationResponseDto } from './education.response.dto.js';
import { ExperienceResponseDto } from './experience.response.dto.js';
import { SkillsResponseDto } from './skill.response.dto.js';

export class ProfileResponseDto {
  id!: string;
  title!: string;
  summary!: string;
  skills!: SkillsResponseDto[];
  experiences!: ExperienceResponseDto[];
  education!: EducationResponseDto[];
}