import { Inject, Injectable, Logger, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import { QueuePoller } from './queue.poller';
import { JobProcessor } from './job.processor';
import { RetryHandler } from './retry.handler';
import { ApplyJobData } from './apply-job.types';

/**
 * Orquestador del procesador de la cola `linkedin-apply`.
 *
 * Coordina tres componentes especializados:
 * - QueuePoller: polling de la cola Redis (RPOP)
 * - JobProcessor: lógica de negocio (claim → apply → mark)
 * - RetryHandler: reintentos con backoff (máx. 3)
 */
@Injectable()
export class ApplyJobProcessor implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(ApplyJobProcessor.name);

  constructor(
    private readonly poller: QueuePoller,
    private readonly jobProcessor: JobProcessor,
    private readonly retryHandler: RetryHandler,
  ) {}

  onModuleInit(): void {
    this.logger.log('[Queue] Apply processor started (polling every 15s).');
    this.poller.start((raw) => this.handleJob(raw));
  }

  onModuleDestroy(): void {
    this.poller.stop();
  }

  /**
   * Procesa un trabajo crudo de la cola: lo parsea y lo delega al JobProcessor.
   * Si falla, delega al RetryHandler.
   */
  private async handleJob(raw: string): Promise<void> {
    try {
      const data = this.parseJob(raw);
      await this.jobProcessor.process(data);
    } catch (error) {
      this.logger.error(
        `[Queue] Error processing job: ${error instanceof Error ? error.message : error}`,
      );
      // Re-encolar para reintentar (máx. 3 intentos)
      await this.retryHandler.retry(data);
    }
  }

  /**
   * Convierte el JSON crudo de la cola en un trabajo tipado.
   */
  private parseJob(raw: string): ApplyJobData {
    return JSON.parse(raw) as ApplyJobData;
  }
}