import { IsOptional, IsString, IsUrl } from "class-validator";

export class CompanyDto {

  @IsOptional()
  @IsString()
  name?: string;

  @IsOptional()
  @IsString()
  industry?: string;

  @IsOptional()
  @IsUrl()
  website?: string;
  @IsOptional()
  @IsUrl()
  linkedinUrl?: string;

  size?: string;
}