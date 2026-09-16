import { EducationVectorDto } from "./dto.vector.education";
import { ExperiencesVectorDto } from "./dto.vector.experiences";
import { SkillsVectorDto } from "./dto.vector.skills";

export class ProfileVectorDto {
    id!: string;
    vector!: number[];
    userId!: string;
    skills!: SkillsVectorDto;
    experiences!: ExperiencesVectorDto;
    education!: EducationVectorDto;
}