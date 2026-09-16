/** Datos que viajan en cada trabajo de la cola (serializados como JSON). */
export interface ApplyJobData {
  /** UUID de la vacante en la BD (jobs.id). */
  jobId: string;
  /** URL de la vacante en LinkedIn. */
  url: string;
  /** Título para logs. */
  title: string;
}