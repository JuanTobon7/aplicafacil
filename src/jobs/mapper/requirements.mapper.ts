import { RequirementsDto } from '../dto/helpers/RequerimentsDto';
import { RequirementsModel } from '../models/requirements.model';

export class RequirementsMapper {

  static toModel(dto: RequirementsDto): RequirementsModel {
    const model = new RequirementsModel();
    model.yearsOfExperience = dto.yearsOfExperience;
    model.skills = dto.skills ? [...dto.skills] : undefined;
    model.technologies = dto.technologies ? [...dto.technologies] : undefined;
    model.languages = dto.languages ? [...dto.languages] : undefined;
    model.education = dto.education ? [...dto.education] : undefined;
    model.certifications = dto.certifications
      ? [...dto.certifications]
      : undefined;
    return model;
  }

  static toDto(model: RequirementsModel): RequirementsDto {
    const dto = new RequirementsDto();
    dto.yearsOfExperience = model.yearsOfExperience;
    dto.skills = model.skills ? [...model.skills] : undefined;
    dto.technologies = model.technologies
      ? [...model.technologies]
      : undefined;
    dto.languages = model.languages ? [...model.languages] : undefined;
    dto.education = model.education ? [...model.education] : undefined;
    dto.certifications = model.certifications
      ? [...model.certifications]
      : undefined;
    return dto;
  }
}
