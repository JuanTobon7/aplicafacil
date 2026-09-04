import { IsNotEmpty, IsOptional, IsString, IsUrl } from "class-validator";

export class JobSourceDto {
  @IsString()
  @IsNotEmpty()
  platform!: string;
  @IsNotEmpty()
  @IsUrl()
  url!: string;
  @IsString()
  @IsOptional()
  externalId?: string;
  @IsString()
  @IsOptional()
  scraperVersion?: string;
}