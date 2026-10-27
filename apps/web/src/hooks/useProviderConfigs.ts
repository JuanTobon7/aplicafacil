import { useCallback, useEffect, useState } from "react";
import { aiApi, isSettling, type ProviderConfig, type ProviderType } from "../api/ai";
import { ApiError } from "../api/http";

const POLL_MS = 1500;

/**
 * Configuraciones de IA y sus tipos. El backend es la fuente de verdad del estado: mientras espera al mcp-gateway
 * (PENDING, INITIALIZING o una prueba en curso) se vuelve a consultar.
 */
export function useProviderConfigs() {
  const [types, setTypes] = useState<ProviderType[]>([]);
  const [configs, setConfigs] = useState<ProviderConfig[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  const reload = useCallback(async () => {
    try {
      setConfigs(await aiApi.list());
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "No se pudieron cargar las configuraciones.");
    }
  }, []);

  useEffect(() => {
    void aiApi.providerTypes().then(setTypes).catch(() => setTypes([]));
    void reload();
  }, [reload]);

  const settling = configs?.some(isSettling) ?? false;
  useEffect(() => {
    if (!settling) return;
    const timer = window.setInterval(() => void reload(), POLL_MS);
    return () => window.clearInterval(timer);
  }, [settling, reload]);

  /** Ejecuta una acción sobre una configuración, la marca ocupada y recarga. */
  const run = async (id: string, action: () => Promise<unknown>) => {
    setError(null);
    setBusy(id);
    try {
      await action();
      await reload();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "La operación falló.");
      if (e instanceof ApiError && e.code === "VERSION_CONFLICT") await reload();
    } finally {
      setBusy(null);
    }
  };

  return { types, configs, error, busy, run, reload };
}
