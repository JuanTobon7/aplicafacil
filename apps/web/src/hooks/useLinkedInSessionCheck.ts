import { useEffect } from "react";
import { useNavigate } from "react-router";
import { linkedinApi } from "../api/linkedin";

const CHECKED_KEY = "af-linkedin-session-checked";

/** Por qué se pide iniciar sesión: lo muestra la página de conexión. */
export type ReconnectReason = "EXPIRED" | "NOT_CONNECTED";

/**
 * Al abrir la app (una vez por pestaña), comprueba que la sesión de LinkedIn guardada siga viva. Si venció o no hay,
 * lleva a iniciar sesión de nuevo para guardar una nueva. Si la comprobación no se puede hacer (scraper apagado,
 * sesión en uso), no interrumpe: se vuelve a intentar al abrir otra vez.
 */
export function useLinkedInSessionCheck(enabled: boolean) {
  const navigate = useNavigate();
  useEffect(() => {
    if (!enabled || alreadyChecked()) return;
    let cancelled = false;
    linkedinApi
      .checkSession()
      .then(({ result }) => {
        if (cancelled) return;
        if (result === "VALID") markChecked();
        if (result === "EXPIRED" || result === "NOT_CONNECTED") {
          navigate("/conectar", { state: { reason: result satisfies ReconnectReason } });
        }
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [enabled, navigate]);
}

function alreadyChecked(): boolean {
  try {
    return sessionStorage.getItem(CHECKED_KEY) === "1";
  } catch {
    return false;
  }
}

function markChecked() {
  try {
    sessionStorage.setItem(CHECKED_KEY, "1");
  } catch {
    // Sin sessionStorage se comprueba en cada carga: solo cuesta una llamada
  }
}
