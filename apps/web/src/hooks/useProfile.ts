import { useCallback, useEffect, useState } from "react";
import { aiApi, type ProviderConfig } from "../api/ai";
import { ApiError } from "../api/http";
import { isTaskPending, profileApi, type AiTask, type Profile } from "../api/profile";

const POLL_MS = 3000;

/**
 * Perfil laboral, tareas de IA y proveedores que se pueden usar. Mientras una tarea esté en curso consulta cada 3 s;
 * cuando termina, vuelve a leer el perfil (el CV ya se aplicó o llegó la búsqueda sugerida).
 */
export function useProfile() {
  const [profile, setProfile] = useState<Profile | null | undefined>(undefined);
  const [tasks, setTasks] = useState<AiTask[]>([]);
  const [providers, setProviders] = useState<ProviderConfig[]>([]);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    try {
      const [loaded, recent] = await Promise.all([loadProfile(), profileApi.tasks()]);
      setProfile(loaded);
      setTasks(recent);
      setError(null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "No se pudo cargar el perfil.");
    }
  }, []);

  useEffect(() => {
    void reload();
    aiApi.list().then((list) => setProviders(list.filter((c) => c.enabled && c.status === "ACTIVE")), () => setProviders([]));
  }, [reload]);

  const pending = tasks.some(isTaskPending);
  useEffect(() => {
    if (!pending) return;
    const timer = window.setInterval(() => void reload(), POLL_MS);
    return () => window.clearInterval(timer);
  }, [pending, reload]);

  return { profile, tasks, providers, pending, error, setError, reload, setProfile };
}

/** 404 = todavía no hay perfil (null), no un error. */
async function loadProfile(): Promise<Profile | null> {
  try {
    return await profileApi.me();
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) return null;
    throw e;
  }
}
