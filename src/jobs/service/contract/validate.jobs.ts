import { JobPostingDto } from "src/jobs/dto/req/JobPostingDto";
import { JobsService } from "./jobs.service";

export abstract class ValidateJobsService {
    abstract validateJobsApplied(jobsService: JobsService, jobs: JobPostingDto[]): Promise<JobPostingDto[]>;
    abstract markJobsAsApplied(jobsService: JobsService, jobs: JobPostingDto[]): Promise<void>;
    abstract markJobsAsPending(jobsService: JobsService, jobs: JobPostingDto[]): Promise<void>;
}