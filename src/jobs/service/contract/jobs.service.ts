import { JobPostingDto } from "src/jobs/dto/req/JobPostingDto";
import { JobsApplymentsModel, StatusApplyment } from "src/jobs/models/jobs.applyments";

export abstract class JobsService {
    abstract getJobs(): Promise<JobsApplymentsModel[]>;
    abstract getJobsByStatus(status: StatusApplyment): Promise<JobsApplymentsModel[]>;
    abstract saveJob(job: JobPostingDto): Promise<JobsApplymentsModel>;
    abstract updateStatusJob(jobId: string, status: StatusApplyment): Promise<JobsApplymentsModel>;
    abstract deleteJob(jobId: string): Promise<void>;
}