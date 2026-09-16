import { Injectable, Logger } from '@nestjs/common';
import { FillFormRequestDto } from '../../dto/req/fill.form.request.dto';
import { JobRecommendationService } from '../contract/job.recommendation.service';
import { FillFormUseCase } from '@aplicafacil/core/application';
import { FillFormResponse } from '@aplicafacil/core/domain';

export type { FillFormResponse };

/**
 * Adapter HTTP (NestJS) sobre el use case FillFormUseCase.
 *
 * Toda la lógica de negocio (e-tags, caché, ensamblado, invocación al LLM)
 * vive en el core. Este service solo delega.
 */
@Injectable()
export class JobRecommendationServiceImpl implements JobRecommendationService {
  private readonly logger = new Logger(JobRecommendationServiceImpl.name);

  constructor(private readonly fillFormUseCase: FillFormUseCase) {}

  async fillFormFields(body: FillFormRequestDto): Promise<FillFormResponse> {
    this.logger.debug(
      `Delegating fillFormFields to FillFormUseCase (${body.fields?.length ?? 0} campos)`,
    );
    return this.fillFormUseCase.execute(body);
  }
}
