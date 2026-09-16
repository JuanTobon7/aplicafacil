import { ProfileResponseDto } from '../../domain/candidate/profile.response.dto.js';
import { CV_SYSTEM_EXTRACT } from '../../prompts/cv.prompt.js';
import { AiCompletionPort } from '../ports/ai-completion.port.js';
import { LoggerPort } from '../ports/logger.port.js';

/**
 * Use case: extraer un perfil estructurado (skills, experiencias, educación)
 * a partir del texto crudo de un CV.
 *
 * No conoce MCP ni HTTP: depende únicamente de AiCompletionPort y LoggerPort.
 */
export class ExtractCvUseCase {
  constructor(
    private readonly ai: AiCompletionPort,
    private readonly logger: LoggerPort,
  ) {}

  async execute(data: string): Promise<ProfileResponseDto> {
    this.logger.debug('Extracting profile from CV...');

    const rawResponse = await this.ai.complete({
      system: 'You are a system that extracts structured data from CVs.',
      prompt: CV_SYSTEM_EXTRACT,
      data,
    });

    // El prompt de CV exige JSON válido parseable por JSON.parse.
    const parsed = JSON.parse(rawResponse) as ProfileResponseDto;
    return parsed;
  }
}