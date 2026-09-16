import { EducationResponseDto } from "./education.response.dto";
import { ExperienceResponseDto } from "./experience.response.dto";
import { SkillsResponseDto } from "./skill.response.dto";

export class ProfileResponseDto {

  id!: string;
  title!: string;
  summary!: string;
  skills!: SkillsResponseDto[];
  experiences!: ExperienceResponseDto[];
  education!: EducationResponseDto[];
}