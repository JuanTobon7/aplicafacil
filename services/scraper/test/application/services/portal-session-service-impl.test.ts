import { describe, expect, it } from "vitest";
import type { JobPortal } from "../../../src/application/port/out/job-portal";
import { InMemoryPortalRegistry } from "../../../src/application/services/in-memory-portal-registry";
import { PortalSessionServiceImpl } from "../../../src/application/services/portal-session-service-impl";
import { incompleteProfile } from "../../../src/domain/profile-snapshot";
import { FakeBrowserSession, FakeSessionFactory } from "../../support/fake-browser";

const portal = (valid: boolean): JobPortal => ({
  id: "stub",
  displayName: "Stub",
  loginUrl: "https://stub.example/login",
  sessionDetector: { isLoginComplete: async () => valid, hasValidSession: async () => valid },
  identityReader: { read: async () => null },
  profileReader: { read: async () => incompleteProfile(["skills"]) },
});

describe("PortalSessionServiceImpl", () => {
  it("abre un contexto headless con el storageState y devuelve el renovado", async () => {
    const session = new FakeBrowserSession();
    const factory = new FakeSessionFactory(() => session);
    const state = { cookies: [{ name: "x" }], origins: [] };

    const result = await new PortalSessionServiceImpl(factory).readProfile(portal(true), state);

    expect(factory.opened[0].options).toEqual({ visible: false, storageState: state });
    expect(result).toEqual({ valid: true, storageState: session.state, profile: incompleteProfile(["skills"]) });
    expect(session.closeCalls).toBe(1);
  });

  it("una sesión vencida responde valid=false sin storageState", async () => {
    const session = new FakeBrowserSession();
    const result = await new PortalSessionServiceImpl(new FakeSessionFactory(() => session)).validate(portal(false), {});
    expect(result).toEqual({ valid: false });
    expect(session.closeCalls).toBe(1);
  });
});

describe("InMemoryPortalRegistry", () => {
  it("encuentra portales por id y rechaza los desconocidos y duplicados", () => {
    const registry = new InMemoryPortalRegistry([portal(true)]);
    expect(registry.get("stub").displayName).toBe("Stub");
    expect(() => registry.get("computrabajo")).toThrow(expect.objectContaining({ code: "UNKNOWN_PORTAL" }));
    expect(() => new InMemoryPortalRegistry([portal(true), portal(true)])).toThrow("Portal duplicado");
  });
});
