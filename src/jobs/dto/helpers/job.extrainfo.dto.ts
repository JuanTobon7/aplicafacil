import { IsNotEmpty, IsOptional, IsString } from "class-validator";
import { EmploymentType } from "src/jobs/enum/employment.yype";
import { WorkplaceType } from "src/jobs/enum/workplace.type";

export class JobExtraInfoDto  {
  @IsString()
  @IsNotEmpty()
  title!: string;
  @IsString()
  @IsOptional()
  description?: string;
  
  @IsString()
  @IsOptional()
  employmentType?: EmploymentType;
  
  @IsString()
  @IsOptional()
  workplaceType?: WorkplaceType;
  
  @IsString()
  @IsOptional()
  seniorityLevel?: string;
  
  @IsString()
  @IsOptional()
  department?: string;
}