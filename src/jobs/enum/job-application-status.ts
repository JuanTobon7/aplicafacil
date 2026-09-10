/**
 * Estados del ciclo de vida de una vacante en el módulo de jobs.
 *
 * Flujo principal:
 * DISCOVERED → MATCHED → APPLYING → APPLIED / APPLICATION_FAILED
 *
 * SKIPPED: la IA determinó que no hay match suficiente.
 * REJECTED: la postulación fue rechazada (manual o por la empresa).
 */
export enum JobApplicationStatus {
  DISCOVERED = 'DISCOVERED',
  MATCHED = 'MATCHED',
  SKIPPED = 'SKIPPED',
  APPLYING = 'APPLYING',
  APPLIED = 'APPLIED',
  APPLICATION_FAILED = 'APPLICATION_FAILED',
  REJECTED = 'REJECTED',
}