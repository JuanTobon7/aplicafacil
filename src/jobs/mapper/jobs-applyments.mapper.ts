import { JobPostingDto } from '../dto/req/job..osting.dto';
import { JobsApplymentsModel } from '../models/jobs.applyments';
import { JobSourceMapper } from './job-source.mapper';
import { RequirementsMapper } from './requirements.mapper';
import { SkillMatchMapper } from './skill-match.mapper';

export class JobsApplymentsMapper {

  static fromJobPosting(job: JobPostingDto): JobsApplymentsModel {
    const applyment = new JobsApplymentsModel();
    applyment.title = job.job.title;
    applyment.match = job.metadata?.skillsMatch
      ? SkillMatchMapper.toModel(job.metadata.skillsMatch)
      : undefined;
    applyment.source = JobSourceMapper.toModel(job.source);
    applyment.requirements = job.requirements
      ? RequirementsMapper.toModel(job.requirements)
      : undefined;
    return applyment;
  }
}
