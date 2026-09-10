import { SkillsMatchDto } from '../dto/helpers/skill.match.dto';
import { SkillMatchModel } from '../models/skill-match.model';

export class SkillMatchMapper {

  static toModel(dto: SkillsMatchDto): SkillMatchModel {
    const model = new SkillMatchModel();
    model.matched = dto.matched;
    model.total = dto.total;
    return model;
  }

  static toDto(model: SkillMatchModel): SkillsMatchDto {
    const dto = new SkillsMatchDto();
    dto.matched = model.matched;
    dto.total = model.total;
    return dto;
  }
}
