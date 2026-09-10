import { Injectable, Logger } from '@nestjs/common';
import { RedisService } from 'src/common/redis/redis.service';
import { LINKEDIN_APPLY_QUEUE } from '../queue/linkedin.queue.module';
import { ApplyJobData } from './apply-job.types';

/**
 * Maneja reintentos de trabajos fallidos (máx. 3 intentos).
 *
 * Usa una clave Redis por jobId para contar intentos.
 */
@Injectable()
export class RetryHandler {
  private readonly logger = new Logger(RetryHandler.name);
  private readonly MAX_ATTEMPTS = 3;
  private readonly ATTEMPT_TTL_SECONDS = 3600; // 1 hora

  constructor(private readonly redisService: RedisService) {}

  /**
   * Re-encola un trabajo fallido si no ha agotado los intentos.
   * Devuelve true si se re-encoló, false si se agotaron los intentos.
   */
  async retry(data: ApplyJobData): Promise<boolean> {
    const attemptsKey = `${LINKEDIN_APPLY_QUEUE}:attempts:${data.jobId}`;
    const attempts = await this.redisService.get(attemptsKey);
    const current = attempts ? Number(attempts) : 0;

    if (current >= this.MAX_ATTEMPTS - 1) {
      this.logger.error(
        `[Queue] Job ${data.title} failed ${this.MAX_ATTEMPTS} times. Giving up.`,
      );
      await this.redisService.del(attemptsKey);
      return false;
    }

    await this.redisService.set(
      attemptsKey,
      String(current + 1),
      this.ATTEMPT_TTL_SECONDS,
    );
    await this.redisService.queuePush(
      LINKEDIN_APPLY_QUEUE,
      JSON.stringify(data),
    );
    this.logger.warn(
      `[Queue] Job ${data.title} re-enqueued (attempt ${current + 2}/${this.MAX_ATTEMPTS}).`,
    );
    return true;
  }
}