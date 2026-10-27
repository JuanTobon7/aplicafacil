import { ScraperError } from "./scraper-error";

/** Mensajes al usuario del login visible, en un solo lugar. */
export const LoginErrors = {
  alreadyConnecting: () => new ScraperError("ALREADY_CONNECTING", "Ya hay un inicio de sesión en curso."),
  cancelled: () => new ScraperError("CANCELLED", "Cancelaste la conexión."),
  windowClosed: (portalName: string) =>
    new ScraperError("WINDOW_CLOSED", `Cerraste la ventana de ${portalName} antes de terminar de iniciar sesión.`),
  timeout: (timeoutMs: number) =>
    new ScraperError("LOGIN_TIMEOUT", `No se completó el inicio de sesión en ${Math.round(timeoutMs / 60_000)} minutos.`),
  identityUnreadable: (portalName: string) =>
    new ScraperError("IDENTITY_UNREADABLE", `Iniciaste sesión, pero no pudimos leer tu identidad de ${portalName}.`),
  browserFailed: () => new ScraperError("BROWSER_FAILED", "No se pudo abrir o controlar el navegador."),
};
