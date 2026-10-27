/**
 * Cliente de la API local (siempre por el gateway, mismo origen: /api/...).
 *
 * - X-Local-Token: el gateway lo inyecta en index.html en cada arranque; sin él la API responde 403.
 * - Authorization: access token de la plataforma, solo en memoria (nunca en localStorage).
 * - 401: se intenta un refresh (cookie httpOnly af_rt) una sola vez y se repite la petición.
 */

/** Error del backend en formato ProblemDetail (RFC 9457) con un `code` estable. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly code?: string,
    readonly fieldErrors: Record<string, string> = {},
  ) {
    super(message);
  }
}

let accessToken: string | null = null;
let refreshHandler: (() => Promise<boolean>) | null = null;

export function setAccessToken(token: string | null) {
  accessToken = token;
}

/** El AuthProvider registra cómo refrescar la sesión cuando una petición recibe 401. */
export function onUnauthorized(handler: (() => Promise<boolean>) | null) {
  refreshHandler = handler;
}

export function localToken(): string {
  return document.querySelector<HTMLMetaElement>('meta[name="af-local-token"]')?.content ?? "";
}

export function apiHeaders(extra: Record<string, string> = {}): Record<string, string> {
  return {
    "X-Local-Token": localToken(),
    ...(accessToken && { Authorization: `Bearer ${accessToken}` }),
    ...extra,
  };
}

export async function api<T>(method: string, path: string, body?: unknown, retry = true): Promise<T> {
  const res = await send(method, path, body);
  if (res.status === 401 && retry && (await refreshed())) {
    return api<T>(method, path, body, false);
  }
  if (!res.ok) throw await toApiError(res);
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

async function send(method: string, path: string, body?: unknown): Promise<Response> {
  // FormData (subir archivos): el navegador pone el Content-Type multipart con su boundary
  const isForm = body instanceof FormData;
  const hasJson = body !== undefined && !isForm;
  try {
    return await fetch(`/api${path}`, {
      method,
      credentials: "same-origin",
      headers: apiHeaders(hasJson ? { "Content-Type": "application/json" } : {}),
      body: isForm ? body : hasJson ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError(0, "No se pudo contactar al backend local. ¿Está corriendo? (dev-up.ps1)", "BACKEND_DOWN");
  }
}

/** Solo si había sesión y el AuthProvider sabe refrescarla. */
async function refreshed(): Promise<boolean> {
  return accessToken !== null && refreshHandler !== null && (await refreshHandler());
}

async function toApiError(res: Response): Promise<ApiError> {
  const problem = await res.json().catch(() => ({}));
  return new ApiError(res.status, problem.detail ?? gatewayMessage(res.status), problem.code, problem.errors ?? {});
}

function gatewayMessage(status: number): string {
  if (status === 502 || status === 503 || status === 504) {
    return "Un servicio del backend no responde. Revisa que Docker y los servicios estén corriendo (dev-up.ps1).";
  }
  return `Error ${status}`;
}
