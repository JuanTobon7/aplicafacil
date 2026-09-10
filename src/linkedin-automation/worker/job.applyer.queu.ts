
import { Injectable, Logger } from '@nestjs/common';
import { JobModel } from 'src/jobs/models/job.model';
import { RedisService } from 'src/common/redis/redis.service';
import { LINKEDIN_APPLY_QUEUE } from '../queue/linkedin.queue.module';
import { ApplyJobData } from './apply-job.processor';

/**
 * Productor de la cola de aplicación de vacantes (lista Redis).
 *
 * Encola las vacantes pendientes (MATCHED) serializadas como JSON en la
 * lista `linkedin-apply` para que el procesador las aplique una a una.
 */
@Injectable()
export class JobApplyerQueue {
  private readonly logger = new Logger(JobApplyerQueue.name);

  constructor(private readonly redisService: RedisService) {}

  /**
   * Encola una lista de vacantes para su aplicación.
   * Devuelve la cantidad de trabajos encolados.
   */
  async enqueueJobs(jobs: JobModel[]): Promise<number> {
    if (jobs.length === 0) {
      return 0;
    }

    const payloads = jobs.map((job) =>
      JSON.stringify({
        jobId: job.id,
        url: job.url ?? '',
        title: job.title,
      } satisfies ApplyJobData),
    );

    const length = await this.redisService.queuePushMany(
      LINKEDIN_APPLY_QUEUE,
      payloads,
    );

    this.logger.log(
      `[Queue] Enqueued ${payloads.length} jobs for application (queue length: ${length ?? 0}).`,
    );
    return payloads.length;
  }

  /**
   * Devuelve la cantidad de trabajos pendientes en la cola.
   */
  async getPendingCount(): Promise<number> {
    return this.redisService.queueLength(LINKEDIN_APPLY_QUEUE);
  }
}