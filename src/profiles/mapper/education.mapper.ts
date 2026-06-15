import { CreateEducationDto } from "../dto/create.education.dto";
import { EducationResponseDto } from "../dto/education.response.dto";
import { EducationModel } from "../models/education.model";

export class EducationMapper {

    static toEntity(dto: CreateEducationDto): EducationModel {

        const entity = new EducationModel();

        entity.institutionName = dto.institutionName;
        entity.description = dto.description;

        entity.startDate = new Date(dto.startDate);

        if (dto.endDate) {
            entity.endDate = new Date(dto.endDate);
        }

        return entity;
    }

    static toEntities(
        dtos: CreateEducationDto[],
    ): EducationModel[] {
        return dtos.map(this.toEntity);
    }

    static toDto(entity: EducationModel):EducationResponseDto {
        return {
            id: entity.id,
            institutionName: entity.institutionName,
            description: entity.description,
            startDate: entity.startDate,
            endDate: entity.endDate,
        };
    }
}