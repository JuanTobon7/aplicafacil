/**
 * Puerto de logging mínimo para la capa de aplicación.
 *
 * Los use cases no deben depender de un logger concreto (NestJS Logger,
 * console, pino, etc.). Este port permite inyectar cualquier implementación.
 */
export interface LoggerPort {
  debug(message: string, ...args: unknown[]): void;
  info(message: string, ...args: unknown[]): void;
  warn(message: string, ...args: unknown[]): void;
  error(message: string, ...args: unknown[]): void;
}