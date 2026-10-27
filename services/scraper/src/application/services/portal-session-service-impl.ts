import type { StorageState } from "../../domain/storage-state";
import type { PortalSessionService, SessionResult } from "../port/in/portal-session-service";
import type { BrowserSession } from "../port/out/browser-session";
import type { BrowserSessionFactory } from "../port/out/browser-session-factory";
import type { JobPortal } from "../port/out/job-portal";

/**
 * Operaciones headless con una sesión guardada. Cada una abre un contexto NO persistente desde el storageState
 * recibido, valida que siga iniciada y devuelve el estado renovado (los portales renuevan cookies).
 */
export class PortalSessionServiceImpl implements PortalSessionService {
  constructor(private readonly sessions: BrowserSessionFactory) {}

  validate(portal: JobPortal, storageState: StorageState) {
    return this.withSession(portal, storageState, async () => ({}));
  }

  readProfile(portal: JobPortal, storageState: StorageState) {
    return this.withSession(portal, storageState, async (session) => ({ profile: await portal.profileReader.read(session) }));
  }

  private async withSession<T extends object>(
    portal: JobPortal,
    storageState: StorageState,
    action: (session: BrowserSession) => Promise<T>,
  ): Promise<SessionResult & Partial<T>> {
    const session = await this.sessions.openSession({ visible: false, storageState });
    try {
      if (!(await portal.sessionDetector.hasValidSession(session))) return { valid: false } as SessionResult & Partial<T>;
      const extra = await action(session);
      return { valid: true, storageState: await session.exportState(), ...extra };
    } finally {
      await session.close().catch(() => undefined);
    }
  }
}
