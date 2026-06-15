import { IsNumber, IsOptional, IsString } from "class-validator";
import { SalaryPeriod } from "src/jobs/enum/SalaryPeriod";

export class CompensationDto   {
  @IsOptional()
  @IsString()
  currency?: string;

  @IsOptional()
  @IsNumber()
  minSalary?: number;
  
  @IsOptional()
  @IsNumber()
  maxSalary?: number;

  salaryPeriod?: SalaryPeriod;
}