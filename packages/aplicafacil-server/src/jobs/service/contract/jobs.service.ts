import { JobPostingDto } from 'src/jobs/dto/req/job..osting.dto';
import { JobModel } from 'src/jobs/models/job.model';
import { JobApplicationStatus } from 'src/jobs/enum/job-application-status';

export abstract class JobsService {
  abstract getJobs(): Promise<JobModel[]>;
  abstract getJobsByStatus(status: JobApplicationStatus): Promise<JobModel[]>;
  abstract getJobsByStatusAndUser(
    status: JobApplicationStatus,
    userId: string,
  ): Promise<JobModel[]>;
  abstract saveJob(
    job: JobPostingDto,
    userId: string,
    profileId?: string,
  ): Promise<JobModel>;
  abstract updateStatusJob(
    jobId: string,
    status: JobApplicationStatus,
    reason?: string,
    detail?: Record<string, unknown>,
  ): Promise<JobModel>;
  abstract deleteJob(jobId: string): Promise<void>;
  abstract getAppliedExternalIds(userId: string): Promise<Set<string>>;
  abstract findJobByUrlAndUser(
    url: string,
    userId: string,
  ): Promise<JobModel | null>;
  /**
   * Claim atómico: transiciona la vacante SOLO si está en `fromStatus`.
   * Devuelve null si otro worker ya la tomó (UPDATE no afectó filas).
   */
  abstract claimJob(
    jobId: string,
    fromStatus: JobApplicationStatus,
    toStatus: JobApplicationStatus,
    detail?: Record<string, unknown>,
  ): Promise<JobModel | null>;
}
