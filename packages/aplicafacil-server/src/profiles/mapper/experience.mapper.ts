import { CreateExperienceDto } from "../dto/create.experience.dto";
import { ExperienceResponseDto } from "../dto/experience.response.dto";
import { ExperiencesModel } from "../models/experiences.model";

export class ExperienceMapper {

    static toEntity(dto: CreateExperienceDto): ExperiencesModel {

        const entity = new ExperiencesModel();

        entity.companyName = dto.companyName;
        entity.position = dto.position;
        entity.description = dto.description;

        entity.startDate = new Date(dto.startDate);

        if (dto.endDate) {
            entity.endDate = new Date(dto.endDate);
        }

        return entity;
    }

    static toEntities(
        dtos: CreateExperienceDto[],
    ): ExperiencesModel[] {
        return dtos.map(this.toEntity);
    }

    static toDto(entity: ExperiencesModel): ExperienceResponseDto {
        return {
            id: entity.id,
            companyName: entity.companyName,
            position: entity.position,
            description: entity.description,
            startDate: entity.startDate,
            endDate: entity.endDate,
        };
    }
}