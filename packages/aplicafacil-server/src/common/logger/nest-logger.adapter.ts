import { Logger } from '@nestjs/common';
import { LoggerPort } from '@aplicafacil/core/application';

/**
 * Adaptador de infraestructura: expone el Logger de NestJS
 * a través del puerto LoggerPort de la capa de aplicación.
 */
export class NestLoggerAdapter implements LoggerPort {
  private readonly logger: Logger;

  constructor(context: string) {
    this.logger = new Logger(context);
  }

  debug(message: string, ...args: unknown[]): void {
    this.logger.debug(message, ...args);
  }

  info(message: string, ...args: unknown[]): void {
    this.logger.log(message, ...args);
  }

  warn(message: string, ...args: unknown[]): void {
    this.logger.warn(message, ...args);
  }

  error(message: string, ...args: unknown[]): void {
    this.logger.error(message, ...args);
  }
}