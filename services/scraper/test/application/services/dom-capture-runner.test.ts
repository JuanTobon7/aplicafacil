import { describe, expect, it } from "vitest";
import type { CaptureScript, DomSnapshotStore } from "../../../src/application/port/out/dom-capture";
import { DomCaptureRunner } from "../../../src/application/services/dom-capture-runner";
import type { DomSnapshot } from "../../../src/domain/dom-snapshot";
import { computrabajoCaptureScript, linkWithText } from "../../../src/infrastructure/portal/computrabajo/computrabajo.capture";
import { linkedInCaptureScript } from "../../../src/infrastructure/portal/linkedin/linkedin.capture";
import { FakeBrowserSession, FakeClock, FakeSessionFactory } from "../../support/fake-browser";

class MemoryStore implements DomSnapshotStore {
  readonly saved: DomSnapshot[] = [];
  async save(snapshot: DomSnapshot) {
    this.saved.push(snapshot);
    return `${snapshot.portal}/${snapshot.step}.html`;
  }
}

const SCRIPT: CaptureScript = {
  portal: "demo",
  displayName: "Demo",
  loginUrl: "https://demo.example/login",
  origin: "https://demo.example",
  loggedIn: ({ cookieNames }) => cookieNames.includes("sess"),
  steps: [
    { name: "listado", description: "Listado", url: "https://demo.example/jobs" },
    { name: "detalle", description: "Detalle", url: (html) => (html.includes('href="/job/7"') ? "https://demo.example/job/7" : null) },
  ],
};

/** La persona "inicia sesión" al tercer sondeo: aparece la cookie de sesión. */
function setup() {
  const session = new FakeBrowserSession({
    cookies: [{ name: "lang", value: "es" }],
    pages: {
      "https://demo.example/login": "<title>Ingresar</title><form><input name=email value=x></form>",
      "https://demo.example/jobs": '<title>Empleos</title><a href="/job/7">Oferta</a><p>yo@example.com</p>',
      "https://demo.example/job/7": "<title>Oferta 7</title><main>Detalle</main>",
    },
  });
  const clock = new FakeClock();
  const sleep = clock.sleep.bind(clock);
  let polls = 0;
  clock.sleep = async (ms: number) => {
    if (++polls === 2) session.site.cookies = [{ name: "lang", value: "es" }, { name: "sess", value: "SECRETO" }];
    return sleep(ms);
  };
  const store = new MemoryStore();
  const said: string[] = [];
  const runner = new DomCaptureRunner({
    sessions: new FakeSessionFactory(() => session),
    store,
    operator: { say: (m) => said.push(m), continueRequested: async () => false },
    clock,
  });
  return { session, store, said, runner };
}

describe("DomCaptureRunner", () => {
  it("espera a detectar el login y luego guarda cada paso sola, saneado y en orden", async () => {
    const { session, store, runner } = setup();

    const saved = await runner.run(SCRIPT, []);

    expect(saved).toBe(4);
    expect(store.saved.map((s) => s.step)).toEqual(["00-login", "01-sesion-iniciada", "02-listado", "03-detalle"]);
    expect(store.saved[0].cookieNames).toEqual(["lang"]);
    expect(store.saved[2].cookieNames).toEqual(["lang", "sess"]);
    expect(store.saved[2].html).not.toContain("yo@example.com");
    expect(JSON.stringify(store.saved)).not.toContain("SECRETO"); // nunca valores de cookies
    expect(session.closeCalls).toBe(1);
  });

  it("la señal manual sigue aunque el guion no detecte la sesión, y muestra lo que va viendo", async () => {
    const session = new FakeBrowserSession({ pages: { "https://demo.example/login": "<title>x</title>" } });
    const said: string[] = [];
    let polls = 0;
    const runner = new DomCaptureRunner({
      sessions: new FakeSessionFactory(() => session),
      store: new MemoryStore(),
      operator: { say: (m) => said.push(m), continueRequested: async () => ++polls >= 3 },
      clock: new FakeClock(),
    });

    await runner.run({ ...SCRIPT, loggedIn: () => false, steps: [] }, []);

    expect(polls).toBe(3);
    expect(said.filter((m) => m.startsWith("  · https://demo.example/login"))).toHaveLength(1); // solo cuando cambia
  });

  it("si la persona cierra la ventana antes de iniciar sesión, avisa en vez de quedarse esperando", async () => {
    const { session, runner } = setup();
    session.open = false;

    await expect(runner.run(SCRIPT, [])).rejects.toThrow(/Cerraste la ventana/);
  });

  it("omite un paso cuyo destino no aparece en la página anterior y sigue", async () => {
    const { store, said, runner } = setup();
    const script: CaptureScript = { ...SCRIPT, steps: [{ name: "detalle", description: "Detalle", url: () => null }] };

    await runner.run(script, []);

    expect(store.saved.map((s) => s.step)).toEqual(["00-login", "01-sesion-iniciada"]);
    expect(said.some((m) => m.includes('Se omite "detalle"'))).toBe(true);
  });
});

describe("guiones de captura", () => {
  it("LinkedIn da la sesión por iniciada con li_at y fuera de login/verificación", () => {
    const { loggedIn } = linkedInCaptureScript("java");
    const probe = (url: string, cookieNames: string[]) => loggedIn({ url, cookieNames, initialCookieNames: [] });
    expect(probe("https://www.linkedin.com/feed/", ["li_at"])).toBe(true);
    expect(probe("https://www.linkedin.com/checkpoint/challenge", ["li_at"])).toBe(false);
    expect(probe("https://www.linkedin.com/feed/", ["JSESSIONID"])).toBe(false);
  });

  it("LinkedIn abre el detalle de la primera oferta del listado", () => {
    const detail = linkedInCaptureScript("java").steps.find((s) => s.name === "detalle-oferta");
    const url = typeof detail?.url === "function" ? detail.url('<li data-occludable-job-id="4012345678">') : null;
    expect(url).toBe("https://www.linkedin.com/jobs/view/4012345678/");
  });

  it("Computrabajo detecta una cookie de autenticación nueva fuera de la página de acceso", () => {
    const { loggedIn, extraCookieOrigins } = computrabajoCaptureScript("java");
    const probe = (url: string, cookieNames: string[], initialCookieNames: string[] = []) =>
      loggedIn({ url, cookieNames, initialCookieNames });
    expect(probe("https://candidato.co.computrabajo.com/", ["ct_auth"])).toBe(true);
    expect(probe("https://candidato.co.computrabajo.com/acceso/", ["ct_auth"])).toBe(false);
    expect(probe("https://co.computrabajo.com/", ["ct_auth"], ["ct_auth"])).toBe(false);
    expect(extraCookieOrigins).toEqual(["https://candidato.co.computrabajo.com"]);
  });

  it("Computrabajo sigue los enlaces del menú y abre la primera oferta", () => {
    const menu = '<nav><a href="/candidato/mis-postulaciones"><span>Mis</span> postulaciones</a><a href="#x">x</a></nav>';
    expect(linkWithText(menu, /postulaci/i, "https://co.computrabajo.com")).toBe(
      "https://co.computrabajo.com/candidato/mis-postulaciones",
    );
    const detail = computrabajoCaptureScript("desarrollador java").steps.find((s) => s.name === "detalle-oferta");
    const html = '<h2><a class="js-o-link fc_base" href="/ofertas-de-trabajo/oferta-de-trabajo-de-dev-ABC123">Dev</a></h2>';
    const url = typeof detail?.url === "function" ? detail.url(html) : null;
    expect(url).toBe("https://co.computrabajo.com/ofertas-de-trabajo/oferta-de-trabajo-de-dev-ABC123");
  });
});
