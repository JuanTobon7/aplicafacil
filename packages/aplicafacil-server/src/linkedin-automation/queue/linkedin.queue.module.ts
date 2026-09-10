import { Module } from '@nestjs/common';
import { ApplyJobProcessor } from '../worker/apply-job.processor';
import { JobApplyerQueue } from '../worker/job.applyer.queu';
import { QueuePoller } from '../worker/queue.poller';
import { JobProcessor } from '../worker/job.processor';
import { RetryHandler } from '../worker/retry.handler';
import { RedisModule } from '../../common/redis/redis.module';

/** Nombre de la cola de aplicación de vacantes (lista Redis). */
export const LINKEDIN_APPLY_QUEUE = 'linkedin-apply';

/**
 * Cola de aplicación de vacantes basada en Redis (listas LPUSH/RPOP).
 *
 * Usa el RedisService existente (con degradación silenciosa): si Redis
 * no está disponible, las operaciones de cola no lanzan excepciones y
 * el sistema continúa funcionando.
 */
@Module({
  imports: [RedisModule],
  providers: [
    JobApplyerQueue,
    ApplyJobProcessor,
    QueuePoller,
    JobProcessor,
    RetryHandler,
  ],
  exports: [JobApplyerQueue],
})
export class LinkedinQueueModule {}