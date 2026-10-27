import type { BrowserSession } from "../port/out/browser-session";
import type { Clock } from "../port/out/clock";
import type { JobPortal } from "../port/out/job-portal";
import type { LoginAttempt } from "./login-attempt";
import { LoginErrors } from "../exception/login-errors";

/**
 * Espera a que la persona termine de iniciar sesión (2FA y captcha incluidos). La app nunca ve la contraseña:
 * solo pregunta al portal, cada `pollMs`, si el login ya se completó.
 */
export class LoginWatcher {
  constructor(
    private readonly clock: Clock,
    private readonly pollMs = 1_000,
  ) {}

  async waitForLogin(session: BrowserSession, portal: JobPortal, attempt: LoginAttempt, timeoutMs: number) {
    const deadline = this.clock.now() + timeoutMs;
    for (;;) {
      if (attempt.cancelled) throw LoginErrors.cancelled();
      if (!session.isOpen()) throw LoginErrors.windowClosed(portal.displayName);
      if (await portal.sessionDetector.isLoginComplete(session)) return;
      if (this.clock.now() > deadline) throw LoginErrors.timeout(timeoutMs);
      await this.clock.sleep(this.pollMs);
    }
  }
}
