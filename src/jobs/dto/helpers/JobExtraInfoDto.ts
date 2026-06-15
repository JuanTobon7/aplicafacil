import { IsNotEmpty, IsOptional, IsString } from "class-validator";
import { EmploymentType } from "src/jobs/enum/EmploymentType";
import { WorkplaceType } from "src/jobs/enum/WorkplaceType";

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