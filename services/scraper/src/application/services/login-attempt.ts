import type { BrowserSession } from "../port/out/browser-session";

/** Un intento de login visible: si se cancela, cierra su ventana aunque se haya abierto después. */
export class LoginAttempt {
  private cancelledFlag = false;
  private browserSession?: BrowserSession;

  constructor(readonly id: string) {}

  get cancelled(): boolean {
    return this.cancelledFlag;
  }

  get session(): BrowserSession | undefined {
    return this.browserSession;
  }

  attach(session: BrowserSession) {
    this.browserSession = session;
    if (this.cancelledFlag) void session.close().catch(() => undefined);
  }

  cancel() {
    this.cancelledFlag = true;
    void this.browserSession?.close().catch(() => undefined);
  }

  async dispose() {
    await this.browserSession?.close().catch(() => undefined);
  }
}
