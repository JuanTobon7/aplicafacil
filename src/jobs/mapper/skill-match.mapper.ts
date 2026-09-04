import { SkillMatchDto } from '../dto/helpers/SkillMatchDto';
import { SkillMatchModel } from '../models/skill-match.model';

export class SkillMatchMapper {

  static toModel(dto: SkillMatchDto): SkillMatchModel {
    const model = new SkillMatchModel();
    model.matched = dto.matched;
    model.total = dto.total;
    return model;
  }

  static toDto(model: SkillMatchModel): SkillMatchDto {
    const dto = new SkillMatchDto();
    dto.matched = model.matched;
    dto.total = model.total;
    return dto;
  }
}
