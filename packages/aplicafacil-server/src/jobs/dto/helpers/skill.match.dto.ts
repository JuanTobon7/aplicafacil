import { IsNumber } from 'class-validator';
 
export class SkillsMatchDto {
  @IsNumber()
  matched!: number;
 
  @IsNumber()
  total!: number;
}