import { useCallback, useEffect, useState } from "react";
import { aiApi, type ProviderConfig } from "../api/ai";
import { ApiError } from "../api/http";
import { profileApi, type AiTask } from "../api/profile";

const POLL_MS = 2000;

export type PlanState =
  | { phase: "idle" }
  | { phase: "thinking"; task: AiTask }
  | { phase: "launched" }
  | { phase: "failed"; message: string };

/**
 * "Buscar con IA": pide a la IA el plan de búsquedas del perfil (profile-service) y sigue la tarea hasta que termina.
 * Al terminar, jobs-service ya recibió el plan por RabbitMQ y encola las búsquedas; `onLaunched` refresca la lista.
 */
export function useAiSearchPlan(onLaunched: () => Promise<void>) {
  const providers = useActiveProviders();
  const [state, setState] = useState<PlanState>({ phase: "idle" });

  const start = useCallback(async () => {
    const config = providers?.[0];
    if (!config) return;
    try {
      setState({ phase: "thinking", task: await profileApi.planSearches(config.id) });
    } catch (e) {
      setState({ phase: "failed", message: e instanceof ApiError ? e.message : "No se pudo pedir el plan a la IA." });
    }
  }, [providers]);

  const taskId = state.phase === "thinking" ? state.task.id : null;
  useEffect(() => {
    if (!taskId) return;
    const timer = window.setInterval(() => {
      profileApi.task(taskId).then((task) => {
        if (task.status === "PENDING") return;
        setState(task.status === "COMPLETED" ? { phase: "launched" } : { phase: "failed", message: taskError(task) });
        if (task.status === "COMPLETED") void refreshWhileArriving(onLaunched);
      }, () => undefined);
    }, POLL_MS);
    return () => window.clearInterval(timer);
  }, [taskId, onLaunched]);

  return { providers, state, start };
}

/** Configuraciones de IA activas; null mientras cargan. */
function useActiveProviders(): ProviderConfig[] | null {
  const [providers, setProviders] = useState<ProviderConfig[] | null>(null);
  useEffect(() => {
    aiApi.list().then((list) => setProviders(list.filter((c) => c.enabled && c.status === "ACTIVE")), () => setProviders([]));
  }, []);
  return providers;
}

function taskError(task: AiTask): string {
  if (task.status === "TIMED_OUT") return "La IA no respondió a tiempo. Revisa el proveedor e inténtalo de nuevo.";
  return task.errorMessage ?? "La IA no pudo elegir las búsquedas.";
}

/** Las búsquedas llegan a jobs-service un instante después (outbox → RabbitMQ): se consulta unas veces más. */
async function refreshWhileArriving(refresh: () => Promise<void>) {
  for (const delay of [0, 1500, 4000]) {
    await new Promise((resolve) => window.setTimeout(resolve, delay));
    await refresh();
  }
}
