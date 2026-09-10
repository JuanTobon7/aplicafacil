import { CreateSkillDto } from "../dto/create.skills.dto";
import { SkillsResponseDto } from "../dto/skill.response.dto";
import { SkillsModel } from "../models/skills.model";

export class SkillMapper {

    static toEntity(dto: CreateSkillDto): SkillsModel {

        const entity = new SkillsModel();

        entity.name = dto.name;
        entity.description = dto.description;
        entity.yearsOfExperience = dto.yearsOfExperience;

        return entity;
    }

    static toEntities(dtos: CreateSkillDto[]): SkillsModel[] {
        return dtos.map(this.toEntity);
    }

    static toDto(entity: SkillsModel): SkillsResponseDto {
        return {
            id: entity.id,
            name: entity.name,
            description: entity.description,
            yearsOfExperience: entity.yearsOfExperience,
        }
    }

}