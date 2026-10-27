import { beforeEach, describe, expect, it } from "vitest";
import type { JobPortal } from "../../../src/application/port/out/job-portal";
import { PortalLoginServiceImpl } from "../../../src/application/services/portal-login-service-impl";
import { LoginAttemptRegistry } from "../../../src/application/services/login-attempt-registry";
import { LoginWatcher } from "../../../src/application/services/login-watcher";
import type { ConnectStep } from "../../../src/domain/connect-step";
import { incompleteProfile } from "../../../src/domain/profile-snapshot";
import { FakeBrowserSession, FakeClock, FakeSessionFactory, silentLogger } from "../../support/fake-browser";

/** Portal cualquiera: el servicio no sabe que existe LinkedIn. */
function stubPortal(overrides: Partial<JobPortal<{ user: string }>> = {}): JobPortal<{ user: string }> {
  return {
    id: "stub",
    displayName: "Stub",
    loginUrl: "https://stub.example/login",
    sessionDetector: {
      isLoginComplete: async (s) => s.currentUrl() === "https://stub.example/home",
      hasValidSession: async () => true,
    },
    identityReader: { read: async () => ({ user: "ana" }) },
    profileReader: { read: async () => incompleteProfile(["summary"]) },
    ...overrides,
  };
}

describe("PortalLoginServiceImpl", () => {
  let clock: FakeClock;
  let session: FakeBrowserSession;
  let factory: FakeSessionFactory;
  let useCase: PortalLoginServiceImpl;
  let steps: ConnectStep[];

  beforeEach(() => {
    clock = new FakeClock();
    session = new FakeBrowserSession({ redirects: { "https://stub.example/login": "https://stub.example/home" } });
    factory = new FakeSessionFactory(() => session);
    useCase = new PortalLoginServiceImpl(factory, new LoginAttemptRegistry(), new LoginWatcher(clock), silentLogger);
    steps = [];
  });

  const run = (portal = stubPortal(), timeoutMs = 60_000) =>
    useCase.execute(portal, "a1", { timeoutMs }, (step) => steps.push(step));

  it("abre una ventana visible, espera el login y devuelve identidad, perfil y sesión", async () => {
    useCase.reserve("a1");
    const result = await run();

    expect(factory.opened[0].options).toEqual({ visible: true });
    expect(steps).toEqual(["OPENING_BROWSER", "WAITING_FOR_LOGIN", "READING_PROFILE"]);
    expect(result).toEqual({ identity: { user: "ana" }, profile: incompleteProfile(["summary"]), storageState: session.state });
    expect(session.closeCalls).toBeGreaterThan(0);
  });

  it("un solo intento a la vez, y se libera al terminar", async () => {
    useCase.reserve("a1");
    expect(() => useCase.reserve("a2")).toThrow(expect.objectContaining({ code: "ALREADY_CONNECTING" }));
    await run();
    expect(() => useCase.reserve("a2")).not.toThrow();
  });

  it("no reserva si el navegador no está listo", () => {
    factory.ready = false;
    expect(() => useCase.reserve("a1")).toThrow(expect.objectContaining({ code: "BROWSER_NOT_READY" }));
  });

  it("vence el tiempo si la persona no inicia sesión", async () => {
    session.site = {};
    useCase.reserve("a1");
    await expect(run(stubPortal(), 5 * 60_000)).rejects.toMatchObject({
      code: "LOGIN_TIMEOUT",
      message: "No se completó el inicio de sesión en 5 minutos.",
    });
  });

  it("cancelar cierra la ventana y responde CANCELLED", async () => {
    session.site = {};
    clock.onSleep = () => useCase.cancel("a1");
    useCase.reserve("a1");
    await expect(run()).rejects.toMatchObject({ code: "CANCELLED" });
    expect(session.open).toBe(false);
  });

  it("si la persona cierra la ventana responde WINDOW_CLOSED con el nombre del portal", async () => {
    session.site = {};
    clock.onSleep = () => (session.open = false);
    useCase.reserve("a1");
    await expect(run()).rejects.toMatchObject({
      code: "WINDOW_CLOSED",
      message: "Cerraste la ventana de Stub antes de terminar de iniciar sesión.",
    });
  });

  it("sin identidad responde IDENTITY_UNREADABLE", async () => {
    useCase.reserve("a1");
    await expect(run(stubPortal({ identityReader: { read: async () => null } }))).rejects.toMatchObject({
      code: "IDENTITY_UNREADABLE",
    });
  });

  it("si falla la lectura del perfil, entrega uno incompleto", async () => {
    useCase.reserve("a1");
    const portal = stubPortal({ profileReader: { read: async () => Promise.reject(new Error("dom")) } });
    expect((await run(portal)).profile).toEqual(incompleteProfile(["all"]));
  });
});
