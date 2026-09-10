import { JobPostingDto } from 'src/jobs/dto/req/job..osting.dto';
import { JobsService } from './jobs.service';

export abstract class ValidateJobsService {
  abstract validateJobsApplied(
    jobsService: JobsService,
    jobs: JobPostingDto[],
    userId: string,
  ): Promise<JobPostingDto[]>;
  abstract markJobsAsPending(
    jobsService: JobsService,
    jobs: JobPostingDto[],
    userId: string,
    profileId?: string,
  ): Promise<void>;
  abstract markJobsAsApplied(
    jobsService: JobsService,
    jobs: JobPostingDto[],
    userId: string,
  ): Promise<void>;
}
