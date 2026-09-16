import { Injectable, Logger } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { JobsService } from '../contract/jobs.service';
import { JobPostingDto } from 'src/jobs/dto/req/job..osting.dto';
import { JobModel } from 'src/jobs/models/job.model';
import { JobApplicationStatus } from 'src/jobs/enum/job-application-status';
import { JobsApplymentsMapper } from 'src/jobs/mapper/jobs-applyments.mapper';

@Injectable()
export class JobsServiceImpl implements JobsService {
  private readonly logger = new Logger(JobsServiceImpl.name);

  constructor(
    @InjectRepository(JobModel)
    private readonly jobsRepo: Repository<JobModel>,
  ) {}

  async getJobs(): Promise<JobModel[]> {
    return this.jobsRepo.find({ order: { createdAt: 'DESC' } });
  }

  async getJobsByStatus(status: JobApplicationStatus): Promise<JobModel[]> {
    return this.jobsRepo.find({
      where: { status },
      order: { createdAt: 'DESC' },
    });
  }

  async getJobsByStatusAndUser(
    status: JobApplicationStatus,
    userId: string,
  ): Promise<JobModel[]> {
    return this.jobsRepo.find({
      where: { status, personId: userId },
      order: { createdAt: 'DESC' },
    });
  }

  async saveJob(
    job: JobPostingDto,
    userId: string,
    profileId?: string,
  ): Promise<JobModel> {
    const entity = JobsApplymentsMapper.fromJobPosting(job);
    entity.personId = userId;
    entity.profileId = profileId;
    entity.status = JobApplicationStatus.DISCOVERED;
    const saved = await this.jobsRepo.save(entity);
    this.logger.log(`Job saved: ${saved.id} — ${saved.title}`);
    return saved;
  }

  async updateStatusJob(
    jobId: string,
    status: JobApplicationStatus,
    reason?: string,
    detail?: Record<string, unknown>,
  ): Promise<JobModel> {
    const existing = await this.jobsRepo.findOneBy({ id: jobId });
    if (!existing) {
      throw new Error(`Job ${jobId} not found`);
    }
    existing.status = status;
    if (reason !== undefined) {
      existing.statusReason = reason;
    }
    if (detail !== undefined) {
      existing.applicationDetail = detail;
    }
    if (status === JobApplicationStatus.APPLIED) {
      existing.appliedAt = new Date();
    }
    return this.jobsRepo.save(existing);
  }

  async deleteJob(jobId: string): Promise<void> {
    await this.jobsRepo.delete(jobId);
  }

  /**
   * Devuelve los externalIds (LinkedIn IDs) ya registrados para un usuario,
   * para filtrar vacantes duplicadas en el search worker.
   */
  async getAppliedExternalIds(userId: string): Promise<Set<string>> {
    const all = await this.jobsRepo.find({
      where: { personId: userId },
      select: { url: true },
    });
    const ids = new Set<string>();
    for (const job of all) {
      if (job.url) {
        ids.add(job.url);
      }
    }
    return ids;
  }

  /**
   * Busca una vacante por URL y usuario (idempotencia).
   */
  async findJobByUrlAndUser(
    url: string,
    userId: string,
  ): Promise<JobModel | null> {
    return this.jobsRepo.findOneBy({ url, personId: userId });
  }

  /**
   * Claim atómico con UPDATE condicional:
   * UPDATE jobs SET status = $to WHERE id = $id AND status = $from
   * Si la fila ya fue tomada por otro worker, no se afecta ninguna fila
   * y devolvemos null.
   */
  async claimJob(
    jobId: string,
    fromStatus: JobApplicationStatus,
    toStatus: JobApplicationStatus,
    detail?: Record<string, unknown>,
  ): Promise<JobModel | null> {
    const set: Record<string, unknown> = {
      status: toStatus,
    };
    if (detail !== undefined) {
      set.applicationDetail = detail;
    }

    const result = await this.jobsRepo
      .createQueryBuilder()
      .update(JobModel)
      .set(set)
      .where('id = :jobId AND status = :fromStatus', {
        jobId,
        fromStatus,
      })
      .returning('*')
      .execute();

    const raw = (result.raw as JobModel[] | undefined)?.[0];
    if (!raw) {
      return null;
    }
    return this.jobsRepo.create(raw);
  }
}
