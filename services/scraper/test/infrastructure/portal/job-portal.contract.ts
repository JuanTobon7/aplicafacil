import { describe, expect, it } from "vitest";
import type { JobPortal } from "../../../src/application/port/out/job-portal";
import { BrokenBrowserSession, FakeBrowserSession } from "../../support/fake-browser";

/**
 * Suite de contrato (Liskov): todo JobPortal debe pasarla para que los servicios de aplicación lo usen sin cambios.
 * Al agregar Computrabajo: `describeJobPortalContract("Computrabajo", createComputrabajoPortal)`.
 */
export function describeJobPortalContract(name: string, create: () => JobPortal) {
  describe(`${name} cumple el contrato de JobPortal`, () => {
    it("tiene id para la URL, nombre visible y login por https", () => {
      const portal = create();
      expect(portal.id).toMatch(/^[a-z0-9-]+$/);
      expect(portal.displayName.trim()).not.toBe("");
      expect(new URL(portal.loginUrl).protocol).toBe("https:");
    });

    it("sin sesión responde false en vez de lanzar", async () => {
      const portal = create();
      await expect(portal.sessionDetector.isLoginComplete(new FakeBrowserSession())).resolves.toBe(false);
      await expect(portal.sessionDetector.hasValidSession(new FakeBrowserSession())).resolves.toBe(false);
    });

    it("si no puede leer la identidad devuelve null en vez de lanzar", async () => {
      const portal = create();
      await expect(portal.identityReader.read(new FakeBrowserSession())).resolves.toBeNull();
      await expect(portal.identityReader.read(new BrokenBrowserSession())).resolves.toBeNull();
    });

    it("el perfil siempre tiene la forma de ProfileSnapshot", async () => {
      const profile = await create().profileReader.read(new FakeBrowserSession());
      expect(profile).toMatchObject({
        experience: expect.any(Array),
        education: expect.any(Array),
        skills: expect.any(Array),
        languages: expect.any(Array),
        missingSections: expect.any(Array),
        complete: expect.any(Boolean),
      });
      if (!profile.complete) expect(profile.missingSections.length).toBeGreaterThan(0);
    });

    it("cierra las pestañas que abre", async () => {
      const portal = create();
      const session = new FakeBrowserSession();
      await portal.sessionDetector.hasValidSession(session);
      await portal.identityReader.read(session);
      expect(session.pages.every((p) => p.closed)).toBe(true);
    });
  });
}
