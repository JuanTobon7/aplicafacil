import type { JobPlatform, JobSearchStatus } from "../../api/jobs";

export const PLATFORMS: { value: JobPlatform; label: string; hint: string }[] = [
  { value: "LINKEDIN", label: "LinkedIn", hint: "Usa tu sesión de LinkedIn conectada." },
  { value: "COMPUTRABAJO", label: "Computrabajo", hint: "Listado público; no necesita sesión." },
];

export const STATUS_LABEL: Record<JobSearchStatus, string> = {
  QUEUED: "En cola",
  RUNNING: "Buscando…",
  COMPLETED: "Completada",
  FAILED: "Falló",
};

/** Mensajes para los códigos que la persona puede resolver. */
export const ERROR_HINT: Record<string, string> = {
  NOT_CONNECTED: "Conecta tu cuenta de LinkedIn para buscar allí.",
  SESSION_EXPIRED: "Tu sesión de LinkedIn expiró: vuelve a conectarla.",
  SESSION_BUSY: "Hay otra automatización usando tu cuenta; se reintentará sola.",
  SEARCH_FAILED: "El portal no mostró el listado (puede haber cambiado su página).",
  PORTAL_UNREACHABLE: "No se pudo abrir el portal. Revisa tu conexión a internet.",
  PORTAL_UNAVAILABLE: "El servicio del navegador no responde; se reintentará.",
};

export function platformLabel(platform: JobPlatform): string | undefined {
  return PLATFORMS.find((p) => p.value === platform)?.label;
}

export function badgeClass(status: JobSearchStatus): string {
  if (status === "COMPLETED") return "active";
  return status === "FAILED" ? "failed" : "pending";
}
