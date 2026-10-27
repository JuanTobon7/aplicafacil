import { describe, expect, it } from "vitest";
import { createComputrabajoPortal } from "../../../../src/infrastructure/portal/computrabajo/computrabajo.portal";
import { FakeBrowserSession } from "../../../support/fake-browser";
import { describeJobPortalContract } from "../job-portal.contract";

describeJobPortalContract("Computrabajo", () => createComputrabajoPortal());

const SESSION_COOKIE = { name: "AspNet.SharedCookie", value: "x" };
const ANONYMOUS = [{ name: "ASP.NET_SessionId", value: "x" }, { name: "lout", value: "1" }];

/** Navega a `url` en una pestaña (para que currentUrl la vea). */
async function at(session: FakeBrowserSession, url: string) {
  await (await session.newPage()).goto(url);
  return session;
}

describe("Computrabajo con sesión de candidato", () => {
  const portal = createComputrabajoPortal();

  it("el acceso es el de candidatos del país", () => {
    expect(portal.loginUrl).toBe("https://candidato.co.computrabajo.com/acceso/");
    expect(createComputrabajoPortal("mx").loginUrl).toBe("https://candidato.mx.computrabajo.com/acceso/");
  });

  it("el login termina con la cookie de sesión fuera de las páginas de acceso (no con la sesión anónima)", async () => {
    const detector = portal.sessionDetector;
    const anonymous = await at(new FakeBrowserSession({ cookies: ANONYMOUS }), "https://co.computrabajo.com/");
    expect(await detector.isLoginComplete(anonymous)).toBe(false);

    const onGoogle = await at(new FakeBrowserSession({ cookies: [SESSION_COOKIE] }), "https://accounts.google.com/x");
    expect(await detector.isLoginComplete(onGoogle)).toBe(false);

    const home = await at(new FakeBrowserSession({ cookies: [...ANONYMOUS, SESSION_COOKIE] }), "https://co.computrabajo.com/");
    expect(await detector.isLoginComplete(home)).toBe(true);
  });

  it("una sesión guardada es válida si la portada muestra el bloque del usuario", async () => {
    const valid = new FakeBrowserSession({ cookies: [SESSION_COOKIE], texts: { "[data-info-user]": "Ana Pérez" } });
    const expired = new FakeBrowserSession({ cookies: [SESSION_COOKIE] });

    expect(await portal.sessionDetector.hasValidSession(valid)).toBe(true);
    expect(await portal.sessionDetector.hasValidSession(expired)).toBe(false);
  });

  it("la identidad es el nombre de la cabecera", async () => {
    const session = new FakeBrowserSession({ texts: { "[data-info-user] > span": "  Ana   Pérez " } });
    expect(await portal.identityReader.read(session)).toEqual({ name: "Ana Pérez" });
  });
});
