import { SkillMapper } from './skill.mapper';
import { ExperienceMapper } from './experience.mapper';
import { EducationMapper } from './education.mapper';
import { CreateProfileDto } from '../dto/create.profile.dto';
import { ProfileModel } from '../models/profiles.model';
import { ProfileResponseDto } from '../dto/profile.response.dto';

export class ProfileMapper {

    static toEntity(dto: CreateProfileDto): ProfileModel {

        const entity = new ProfileModel();
        entity.skills = SkillMapper.toEntities(dto.skills ?? []);
        entity.experiences = ExperienceMapper.toEntities(dto.experiences ?? []);
        entity.educations = EducationMapper.toEntities(dto.education ?? []);

        entity.skills.forEach(skill => {
            skill.profile = entity;
        });

        entity.experiences.forEach(experience => {
            experience.profile = entity;
        });

        entity.educations.forEach(education => {
            education.profile = entity;
        });

        return entity;
    }

    static toDto(
        entity: ProfileModel,
    ): ProfileResponseDto {

        return {
            id: entity.id,
            skills: entity.skills?.map(
                    skill => SkillMapper.toDto(skill)
                ) ?? [],
            experiences: entity.experiences?.map(
                    exp => ExperienceMapper.toDto(exp)
                ) ?? [],
            education: entity.educations?.map(
                    edu => EducationMapper.toDto(edu)
                ) ?? [],
        };
    }
}