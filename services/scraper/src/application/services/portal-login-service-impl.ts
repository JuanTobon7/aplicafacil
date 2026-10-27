import type { ConnectStep } from "../../domain/connect-step";
import { ScraperError } from "../exception/scraper-error";
import type { LoginOptions, LoginResult, PortalLoginService } from "../port/in/portal-login-service";
import type { AppLogger } from "../port/out/app-logger";
import type { BrowserSessionFactory } from "../port/out/browser-session-factory";
import type { JobPortal } from "../port/out/job-portal";
import { readProfileOrIncomplete } from "./read-profile-or-incomplete";
import type { LoginAttempt } from "./login-attempt";
import type { LoginAttemptRegistry } from "./login-attempt-registry";
import { LoginErrors } from "../exception/login-errors";
import type { LoginWatcher } from "./login-watcher";

/**
 * Inicio de sesión manual en la página oficial del portal, en una ventana VISIBLE con un contexto nuevo y vacío.
 * No conoce el portal concreto ni el motor del navegador: solo sus abstracciones.
 */
export class PortalLoginServiceImpl implements PortalLoginService {
  constructor(
    private readonly sessions: BrowserSessionFactory,
    private readonly lock: LoginAttemptRegistry,
    private readonly watcher: LoginWatcher,
    private readonly logger: AppLogger,
  ) {}

  reserve(attemptId: string) {
    this.sessions.assertReady();
    this.lock.acquire(attemptId);
  }

  cancel(attemptId: string) {
    this.lock.cancel(attemptId);
  }

  async execute<TIdentity extends object>(
    portal: JobPortal<TIdentity>,
    attemptId: string,
    options: LoginOptions,
    onStep: (step: ConnectStep) => void,
  ): Promise<LoginResult<TIdentity>> {
    const attempt = this.lock.find(attemptId);
    if (!attempt) throw new Error(`El intento ${attemptId} no está reservado`);
    try {
      onStep("OPENING_BROWSER");
      const session = await this.sessions.openSession({ visible: true });
      attempt.attach(session);
      const page = await session.newPage();
      await page.goto(options.loginUrl ?? portal.loginUrl);

      onStep("WAITING_FOR_LOGIN");
      await this.watcher.waitForLogin(session, portal, attempt, options.timeoutMs);

      onStep("READING_PROFILE");
      const identity = await portal.identityReader.read(session);
      if (!identity) throw LoginErrors.identityUnreadable(portal.displayName);
      const profile = await readProfileOrIncomplete(portal.profileReader, session, this.logger);
      return { identity, profile, storageState: await session.exportState() };
    } catch (error) {
      throw this.translate(error, attempt, portal);
    } finally {
      await this.lock.release(attempt);
    }
  }

  private translate(error: unknown, attempt: LoginAttempt, portal: JobPortal): ScraperError {
    if (error instanceof ScraperError) return error;
    if (attempt.cancelled) return LoginErrors.cancelled();
    const message = String((error as Error)?.message ?? "");
    if (message.includes("closed") || attempt.session?.isOpen() === false) {
      return LoginErrors.windowClosed(portal.displayName);
    }
    this.logger.error(`Fallo del navegador: ${(error as Error)?.name}`);
    return LoginErrors.browserFailed();
  }
}
