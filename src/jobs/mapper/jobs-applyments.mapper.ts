import { JobPostingDto } from '../dto/req/job..osting.dto';
import { JobModel } from '../models/job.model';
import { RequirementsMapper } from './requirements.mapper';
import { SkillMatchMapper } from './skill-match.mapper';

export class JobsApplymentsMapper {
  static fromJobPosting(job: JobPostingDto): JobModel {
    const applyment = new JobModel();
    applyment.title = job.job.title;
    applyment.company = job.company?.name;
    applyment.location = job.location?.rawLocation;
    applyment.description = job.job.description;
    applyment.url = job.source.url;
    applyment.source = job.source.platform;
    applyment.rawContent = job.rawContent;
    applyment.requirements = job.requirements
      ? JSON.stringify(RequirementsMapper.toModel(job.requirements))
      : undefined;
    applyment.matchScore = job.metadata?.skillsMatch
      ? SkillMatchMapper.toModel(job.metadata.skillsMatch).matched
      : undefined;
    return applyment;
  }
}
