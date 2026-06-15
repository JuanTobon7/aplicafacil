import { Type } from 'class-transformer';
import {
  IsDate,
  IsOptional,
  IsString,
  ValidateNested,
} from 'class-validator';

import { CompanyDto } from '../helpers/CompanyDto';
import { CompensationDto } from '../helpers/CompensationDto';
import { JobExtraInfoDto } from '../helpers/JobExtraInfoDto';
import { JobMetadataDto } from '../helpers/JobMetadataDto';
import { JobSourceDto } from '../helpers/JobSourceDto';
import { LocationDto } from '../helpers/LocationDto';
import { RequirementsDto } from '../helpers/RequerimentsDto';

export class JobPostingDto {

  @ValidateNested()
  @Type(() => JobSourceDto)
  source!: JobSourceDto;

  @ValidateNested()
  @Type(() => JobExtraInfoDto)
  job!: JobExtraInfoDto;

  @IsOptional()
  @ValidateNested()
  @Type(() => CompanyDto)
  company?: CompanyDto;

  @ValidateNested()
  @Type(() => LocationDto)
  location!: LocationDto;

  @IsOptional()
  @ValidateNested()
  @Type(() => CompensationDto)
  compensation?: CompensationDto;

  @IsOptional()
  @ValidateNested()
  @Type(() => RequirementsDto)
  requirements?: RequirementsDto;

  @IsOptional()
  @ValidateNested()
  @Type(() => JobMetadataDto)
  metadata?: JobMetadataDto;

  @IsOptional()
  @IsString()
  rawContent?: string;

  @IsOptional()
  @IsDate()
  @Type(() => Date)
  extractedAt?: Date;
}