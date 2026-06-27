import { EducationVectorDto } from "./dto.vector.education.js";
import { ExperiencesVectorDto } from "./dto.vector.experiences.js";
import { SkillsVectorDto } from "./dto.vector.skills.js";

export class ProfileVectorDto {
    id!: string;
    vector!: number[];
    userId!: string;
    skills!: SkillsVectorDto;
    experiences!: ExperiencesVectorDto;
    education!: EducationVectorDto;
}