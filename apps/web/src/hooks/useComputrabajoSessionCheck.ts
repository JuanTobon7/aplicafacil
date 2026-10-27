import { useEffect, useState } from "react";
import { computrabajoApi } from "../api/computrabajo";

/**
 * Al abrir la app, comprueba que la sesión de Computrabajo guardada siga viva. Si venció devuelve true y el layout
 * pide iniciar sesión de nuevo (desde Mi perfil). Sin conexión no pide nada: Computrabajo es opcional.
 */
export function useComputrabajoSessionCheck(enabled: boolean): boolean {
  const [expired, setExpired] = useState(false);
  useEffect(() => {
    if (!enabled) return;
    let cancelled = false;
    computrabajoApi
      .checkSession()
      .then(({ result }) => {
        if (!cancelled) setExpired(result === "EXPIRED");
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [enabled]);
  return expired;
}
