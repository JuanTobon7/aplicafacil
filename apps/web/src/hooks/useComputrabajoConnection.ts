import { useCallback, useEffect, useState } from "react";
import { computrabajoApi, type ComputrabajoAttempt, type ComputrabajoStatus } from "../api/computrabajo";
import { ApiError } from "../api/http";

export type ConnectError = { code?: string; message: string };

/**
 * Estado de la conexión con Computrabajo y el intento en curso. Al montar comprueba la sesión (si venció queda
 * EXPIRED y la tarjeta pide iniciar sesión de nuevo); un intento se sigue por polling hasta que termina.
 */
export function useComputrabajoConnection() {
  const [attempt, setAttempt] = useState<ComputrabajoAttempt | null>(null);
  const [error, setError] = useState<ConnectError | null>(null);
  const { status, reload } = useCheckedStatus(setError);
  const attemptId = attempt?.state === "CONNECTING" ? attempt.attemptId : null;
  useFollowAttempt(attemptId, setAttempt, reload);

  const run = (action: () => Promise<unknown>, fallback: string) => async () => {
    setError(null);
    try {
      await action();
    } catch (e) {
      setError(toError(e, fallback));
    }
  };

  return {
    status,
    attempt,
    error,
    connect: run(async () => setAttempt(await computrabajoApi.connect()), "No se pudo abrir Computrabajo."),
    cancel: run(async () => attemptId && (await computrabajoApi.cancel(attemptId)), "No se pudo cancelar."),
    disconnect: run(async () => {
      await computrabajoApi.disconnect();
      setAttempt(null);
      await reload();
    }, "No se pudo desconectar."),
  };
}

/** Comprueba la sesión al montar y luego lee el estado (ya EXPIRED si venció). */
function useCheckedStatus(onError: (e: ConnectError) => void) {
  const [status, setStatus] = useState<ComputrabajoStatus | null>(null);
  const reload = useCallback(async () => {
    try {
      setStatus(await computrabajoApi.status());
    } catch (e) {
      onError(toError(e, "No se pudo leer el estado de Computrabajo."));
    }
  }, [onError]);

  useEffect(() => {
    void computrabajoApi.checkSession().catch(() => undefined).finally(() => void reload());
  }, [reload]);
  return { status, reload };
}

/** Sigue el intento en curso hasta que termina y entonces recarga el estado. */
function useFollowAttempt(
  attemptId: string | null,
  onUpdate: (a: ComputrabajoAttempt) => void,
  reload: () => Promise<void>,
) {
  useEffect(() => {
    if (!attemptId) return;
    return computrabajoApi.followAttempt(attemptId, (next) => {
      onUpdate(next);
      if (next.state !== "CONNECTING") void reload();
    });
  }, [attemptId, onUpdate, reload]);
}

function toError(e: unknown, fallback: string): ConnectError {
  return e instanceof ApiError ? { code: e.code, message: e.message } : { message: fallback };
}
