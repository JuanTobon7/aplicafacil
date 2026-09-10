import { Injectable, Logger } from '@nestjs/common';
import { ValidateJobsService } from '../contract/validate.jobs';
import { JobsService } from '../contract/jobs.service';
import { JobPostingDto } from 'src/jobs/dto/req/job..osting.dto';
import { JobApplicationStatus } from 'src/jobs/enum/job-application-status';
import { JobsServiceImpl } from './jobs.service.impl';

@Injectable()
export class ValidateJobsServiceImpl implements ValidateJobsService {
  private readonly logger = new Logger(ValidateJobsServiceImpl.name);

  /**
   * Filtra las vacantes que ya han sido registradas para el usuario.
   * Solo devuelve las nuevas (no tienen URL registrada para ese usuario).
   */
  async validateJobsApplied(
    jobsService: JobsService,
    jobs: JobPostingDto[],
    userId: string,
  ): Promise<JobPostingDto[]> {
    const jobsServiceImpl = jobsService as JobsServiceImpl;
    const appliedUrls = await jobsServiceImpl.getAppliedExternalIds(userId);

    const newJobs = jobs.filter((job) => {
      const url = job.source?.url;
      return !url || !appliedUrls.has(url);
    });

    this.logger.log(
      `[User ${userId}] Validated ${jobs.length} jobs → ${newJobs.length} new (filtered ${jobs.length - newJobs.length} already known)`,
    );

    return newJobs;
  }

  /**
   * Marca las vacantes como MATCHED (listas para aplicar) para que el
   * worker de apply las procese. El flujo de estados en la BD es:
   * DISCOVERED → MATCHED → APPLYING → APPLIED / APPLICATION_FAILED
   */
  async markJobsAsPending(
    jobsService: JobsService,
    jobs: JobPostingDto[],
    userId: string,
    profileId?: string,
  ): Promise<void> {
    for (const job of jobs) {
      const existing = await jobsService.findJobByUrlAndUser(
        job.source.url,
        userId,
      );
      if (existing) {
        // Idempotencia: si ya existe, solo la marcamos MATCHED si no está aplicada
        if (existing.status !== JobApplicationStatus.APPLIED) {
          await jobsService.updateStatusJob(
            existing.id,
            JobApplicationStatus.MATCHED,
            undefined,
            { automationUserId: userId, profileId },
          );
        }
        continue;
      }
      const saved = await jobsService.saveJob(job, userId, profileId);
      await jobsService.updateStatusJob(
        saved.id,
        JobApplicationStatus.MATCHED,
        undefined,
        { automationUserId: userId, profileId },
      );
    }
    this.logger.log(`[User ${userId}] Marked ${jobs.length} jobs as MATCHED`);
  }

  /**
   * Marca las vacantes como APPLIED (aplicadas exitosamente).
   */
  async markJobsAsApplied(
    jobsService: JobsService,
    jobs: JobPostingDto[],
    userId: string,
  ): Promise<void> {
    for (const job of jobs) {
      const existing = await jobsService.findJobByUrlAndUser(
        job.source.url,
        userId,
      );
      if (existing) {
        await jobsService.updateStatusJob(
          existing.id,
          JobApplicationStatus.APPLIED,
          'Auto-aplicación completada',
        );
      }
    }
    this.logger.log(`[User ${userId}] Marked ${jobs.length} jobs as APPLIED`);
  }
}
