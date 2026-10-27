import { Inject, Injectable } from "@nestjs/common";
import type { Response } from "express";
import { PORTAL_LOGIN_SERVICE, type PortalLoginService } from "../application/port/in/portal-login-service";
import { PORTAL_SESSION_SERVICE, type PortalSessionService } from "../application/port/in/portal-session-service";
import { PORTAL_REGISTRY, type PortalRegistry } from "../application/port/out/portal-registry";
import { ScraperError } from "../application/exception/scraper-error";
import { scraperProperties } from "../infrastructure/config/scraper-properties";
import { NdjsonStream } from "./ndjson-stream";
import { parseLoginRequest, parseStorageState } from "./session-requests";

/**
 * Lógica HTTP compartida por las rutas de sesiones (por portal y las heredadas de LinkedIn). Ninguna respuesta
 * ni error se loguea con el storageState.
 */
@Injectable()
export class SessionsHttpHandler {
  constructor(
    @Inject(PORTAL_REGISTRY) private readonly portals: PortalRegistry,
    @Inject(PORTAL_LOGIN_SERVICE) private readonly loginToPortal: PortalLoginService,
    @Inject(PORTAL_SESSION_SERVICE) private readonly sessions: PortalSessionService,
  ) {}

  /** Streaming NDJSON: eventos `step` y al final un `result` o un `error`. */
  async login(portalId: string, body: unknown, res: Response) {
    const request = parseLoginRequest(body as object);
    const portal = this.portals.get(portalId);
    this.loginToPortal.reserve(request.attemptId);

    const stream = NdjsonStream.open(res);
    try {
      const result = await this.loginToPortal.execute(
        portal,
        request.attemptId,
        { loginUrl: request.loginUrl, timeoutMs: request.timeoutMs ?? scraperProperties.defaultLoginTimeoutMs },
        (step) => stream.send({ type: "step", step }),
      );
      stream.send({ type: "result", ...result });
    } catch (error) {
      const e = error instanceof ScraperError ? error : new ScraperError("BROWSER_FAILED", "Falló el navegador.");
      stream.send({ type: "error", code: e.code, message: e.message });
    } finally {
      stream.end();
    }
  }

  cancel(attemptId: string) {
    this.loginToPortal.cancel(attemptId);
  }

  validate(portalId: string, body: unknown) {
    return this.sessions.validate(this.portals.get(portalId), parseStorageState(body as object));
  }

  readProfile(portalId: string, body: unknown) {
    return this.sessions.readProfile(this.portals.get(portalId), parseStorageState(body as object));
  }
}
