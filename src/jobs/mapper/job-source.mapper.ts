import { JobSourceDto } from '../dto/helpers/job.source.dto';
import { JobSourceModel } from '../models/job-source.model';

export class JobSourceMapper {

  static toModel(dto: JobSourceDto): JobSourceModel {
    const model = new JobSourceModel();
    model.platform = dto.platform;
    model.url = dto.url;
    model.externalId = dto.externalId;
    model.scraperVersion = dto.scraperVersion;
    return model;
  }

  static toDto(model: JobSourceModel): JobSourceDto {
    const dto = new JobSourceDto();
    dto.platform = model.platform;
    dto.url = model.url;
    dto.externalId = model.externalId;
    dto.scraperVersion = model.scraperVersion;
    return dto;
  }
}
