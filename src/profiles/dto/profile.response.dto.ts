import { EducationResponseDto } from "./education.response.dto";
import { ExperienceResponseDto } from "./experience.response.dto";
import { SkillResponseDto } from "./skill.response.dto";

export class ProfileResponseDto {

  id!: string;
  skills!: SkillResponseDto[];
  experiences!: ExperienceResponseDto[];
  education!: EducationResponseDto[];
}