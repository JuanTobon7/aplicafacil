import {
  IsArray,
  ValidateNested,
} from 'class-validator';

import { Type } from 'class-transformer';
import { CreateSkillDto } from './create.skills.dto';
import { CreateExperienceDto } from './create.experience.dto';
import { CreateEducationDto } from './create.education.dto';

export class CreateProfileDto {

  @IsArray()
  @ValidateNested({ each: true })
  @Type(() => CreateSkillDto)
  skills!: CreateSkillDto[];

  @IsArray()
  @ValidateNested({ each: true })
  @Type(() => CreateExperienceDto)
  experiences!: CreateExperienceDto[];

  @IsArray()
  @ValidateNested({ each: true })
  @Type(() => CreateEducationDto)
  education!: CreateEducationDto[];
}