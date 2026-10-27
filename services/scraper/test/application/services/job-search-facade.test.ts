import { describe, expect, it } from "vitest";
import { ScraperError } from "../../../src/application/exception/scraper-error";
import { normalizeCriteria } from "../../../src/application/port/in/job-search-service";
import type { ExtractedRecord } from "../../../src/application/port/out/browser-page";
import type { JobPortal } from "../../../src/application/port/out/job-portal";
import type { JobSearchPortal } from "../../../src/application/port/out/job-search-portal";
import { InMemoryJobSearchPortalRegistry } from "../../../src/application/services/in-memory-job-search-portal-registry";
import { InMemoryPortalRegistry } from "../../../src/application/services/in-memory-portal-registry";
import { JobSearchFacade } from "../../../src/application/services/job-search-facade";
import { jobTitle } from "../../../src/domain/job-title";
import { incompleteProfile } from "../../../src/domain/profile-snapshot";
import { FakeBrowserSession, FakeClock, FakeSessionFactory, silentLogger } from "../../support/fake-browser";

const CARD = "article.job";

/** Portal de prueba: 3 cards por página; la URL lleva el número de página. */
function stubPortal(requiresSession: boolean): JobSearchPortal {
  return {
    id: "stub",
    displayName: "Stub",
    requiresSession,
    pageSize: 3,
    searchUrl: (c, i) => `https://stub.example/search?q=${encodeURIComponent(c.keywords)}&page=${i}`,
    cards: { readyWhen: CARD, emptyWhen: ".empty", item: CARD, fields: { id: { attribute: "data-id" } } },
    toCard: (r: ExtractedRecord) =>
      r.id && r.title
        ? { externalId: r.id, url: `https://stub.example/job/${r.id}`, title: jobTitle(r.title), company: "X", location: null, postedText: null, salaryText: null, quickApply: false }
        : null,
  };
}

const sessionPortal = (valid: boolean): JobPortal => ({
  id: "stub",
  displayName: "Stub",
  loginUrl: "https://stub.example/login",
  sessionDetector: { isLoginComplete: async () => valid, hasValidSession: async () => valid },
  identityReader: { read: async () => null },
  profileReader: { read: async () => incompleteProfile(["skills"]) },
});

const records = (...ids: string[]) => ids.map((id) => ({ id, title: `Oferta ${id}` }));
const url = (page: number) => `https://stub.example/search?q=java&page=${page}`;

function setup(options: { requiresSession?: boolean; validSession?: boolean; pages: ExtractedRecord[][]; texts?: Record<string, string> }) {
  const lists = Object.fromEntries(options.pages.map((cards, i) => [url(i), { [CARD]: cards }]));
  const session = new FakeBrowserSession({ lists, texts: options.texts });
  const factory = new FakeSessionFactory(() => session);
  const clock = new FakeClock();
  const facade = new JobSearchFacade({
    searchPortals: new InMemoryJobSearchPortalRegistry([stubPortal(options.requiresSession ?? false)]),
    portals: new InMemoryPortalRegistry([sessionPortal(options.validSession ?? true)]),
    sessions: factory,
    clock,
    logger: silentLogger,
    limits: { maxPages: 5, pauseBetweenPagesMs: [1000, 2000], listTimeoutMs: 100 },
    random: () => 0.5,
  });
  return { facade, session, factory, clock };
}

describe("JobSearchFacade", () => {
  it("recorre las páginas hasta una incompleta, deduplica y cierra el contexto", async () => {
    const { facade, session, factory, clock } = setup({
      pages: [records("1", "2", "3"), records("3", "4", "5"), records("6")],
    });

    const result = await facade.search("stub", { keywords: "java", maxResults: 50 });

    expect(result.valid).toBe(true);
    expect(result.jobs.map((j) => j.externalId)).toEqual(["1", "2", "3", "4", "5", "6"]);
    expect(result.pagesVisited).toBe(3);
    expect(result.storageState).toBeUndefined(); // portal sin sesión: no hay estado que devolver
    expect(factory.opened[0].options).toEqual({ visible: false, storageState: undefined });
    expect(session.pages[0].scrolls).toBe(3); // desplaza en cada página para cargar las cards perezosas
    expect(clock.current).toBe(3000); // pausa entre páginas (1.5 s x 2), no antes de la primera
    expect(session.pages.every((p) => p.closed)).toBe(true);
    expect(session.closeCalls).toBe(1);
  });

  it("descarta una card que no cumple la regla del título y sigue con las demás", async () => {
    const session = new FakeBrowserSession({
      lists: { [url(0)]: { [CARD]: [{ id: "1", title: "Java Java" }, { id: "2", title: " \n " }, { id: "3", title: "Go" }] } },
    });
    const warnings: string[] = [];
    const facade = new JobSearchFacade({
      searchPortals: new InMemoryJobSearchPortalRegistry([stubPortal(false)]),
      portals: new InMemoryPortalRegistry([sessionPortal(true)]),
      sessions: new FakeSessionFactory(() => session),
      clock: new FakeClock(),
      logger: { ...silentLogger, warn: (m: string) => warnings.push(m) },
      limits: { maxPages: 1, pauseBetweenPagesMs: [0, 0], listTimeoutMs: 100 },
    });

    const result = await facade.search("stub", { keywords: "java", maxResults: 10 });

    expect(result.jobs.map((j) => [j.externalId, j.title])).toEqual([["1", "Java"], ["3", "Go"]]);
    expect(warnings).toEqual(["[stub] Card descartada: Card inválida (title): está vacío"]);
  });

  it("con showBrowser abre una ventana visible y registra cada paso", async () => {
    const session = new FakeBrowserSession({ lists: { [url(0)]: { [CARD]: records("1") } } });
    const factory = new FakeSessionFactory(() => session);
    const logs: string[] = [];
    const facade = new JobSearchFacade({
      searchPortals: new InMemoryJobSearchPortalRegistry([stubPortal(false)]),
      portals: new InMemoryPortalRegistry([sessionPortal(true)]),
      sessions: factory,
      clock: new FakeClock(),
      logger: { ...silentLogger, log: (m: string) => logs.push(m) },
      limits: { maxPages: 5, pauseBetweenPagesMs: [0, 0], listTimeoutMs: 100, showBrowser: true },
    });

    await facade.search("stub", { keywords: "java", maxResults: 10 });

    expect(factory.opened[0].options.visible).toBe(true);
    expect(logs).toEqual([
      '[stub] Abriendo navegador visible para "java" (máx. 10 ofertas)',
      `[stub] Página 1: abriendo ${url(0)}`,
      "[stub] Página 1: listado cargado; desplazando para cargar todas las cards",
      "[stub] Página 1: 1 cards leídas, 1 ofertas nuevas en esta búsqueda",
      "Búsqueda en stub: 1 ofertas en 1 páginas",
    ]);
  });

  it("con recordDir graba la búsqueda en esa carpeta", async () => {
    const factory = new FakeSessionFactory(
      () => new FakeBrowserSession({ lists: { [url(0)]: { [CARD]: records("1") } } }),
    );
    const facade = new JobSearchFacade({
      searchPortals: new InMemoryJobSearchPortalRegistry([stubPortal(false)]),
      portals: new InMemoryPortalRegistry([sessionPortal(true)]),
      sessions: factory,
      clock: new FakeClock(),
      logger: silentLogger,
      limits: { maxPages: 1, pauseBetweenPagesMs: [0, 0], listTimeoutMs: 100, showBrowser: true, recordDir: "rec" },
    });

    await facade.search("stub", { keywords: "java", maxResults: 10 });

    expect(factory.opened[0].options).toEqual({ visible: true, storageState: undefined, recordVideoDir: "rec" });
  });

  it("al grabar recorre cada card a la vista; sin grabar no se detiene en ninguna", async () => {
    const session = new FakeBrowserSession({ lists: { [url(0)]: { [CARD]: records("1", "2") } } });
    const facade = new JobSearchFacade({
      searchPortals: new InMemoryJobSearchPortalRegistry([stubPortal(false)]),
      portals: new InMemoryPortalRegistry([sessionPortal(true)]),
      sessions: new FakeSessionFactory(() => session),
      clock: new FakeClock(),
      logger: silentLogger,
      limits: { maxPages: 1, pauseBetweenPagesMs: [0, 0], listTimeoutMs: 100, showcaseCardMs: 900 },
    });

    await facade.search("stub", { keywords: "java", maxResults: 10 });

    expect(session.pages[0].spotlighted.map((s) => [s.index, s.options.holdMs])).toEqual([[0, 900], [1, 900]]);
  });

  it("se detiene al llegar a maxResults", async () => {
    const { facade, session } = setup({ pages: [records("1", "2", "3"), records("4", "5", "6")] });
    const result = await facade.search("stub", { keywords: "java", maxResults: 4 });
    expect(result.jobs).toHaveLength(4);
    expect(session.pages[0].visited).toEqual([url(0), url(1)]);
  });

  it("se detiene si una página solo repite ofertas ya vistas", async () => {
    const { facade } = setup({ pages: [records("1", "2", "3"), records("1", "2", "3"), records("7", "8", "9")] });
    const result = await facade.search("stub", { keywords: "java", maxResults: 50 });
    expect(result.pagesVisited).toBe(2);
    expect(result.jobs).toHaveLength(3);
  });

  it("descarta las cards que el portal no puede leer", async () => {
    const { facade } = setup({ pages: [[{ id: "1", title: "Buena" }, { id: null, title: "Sin id" }, { id: "2", title: null }]] });
    const result = await facade.search("stub", { keywords: "java", maxResults: 50 });
    expect(result.jobs.map((j) => j.externalId)).toEqual(["1"]);
  });

  it("una búsqueda sin resultados devuelve una lista vacía", async () => {
    const { facade } = setup({ pages: [], texts: { ".empty": "No hay ofertas" } });
    const result = await facade.search("stub", { keywords: "java", maxResults: 10 });
    expect(result).toMatchObject({ valid: true, jobs: [], pagesVisited: 1 });
  });

  it("si el listado no aparece en la primera página falla con SEARCH_FAILED", async () => {
    const { facade, session } = setup({ pages: [] });
    await expect(facade.search("stub", { keywords: "java", maxResults: 10 })).rejects.toMatchObject({ code: "SEARCH_FAILED" });
    expect(session.closeCalls).toBe(1);
  });

  describe("portal con sesión", () => {
    const state = { cookies: [{ name: "li_at" }], origins: [] };

    it("usa el storageState, valida la sesión y devuelve el renovado", async () => {
      const { facade, factory, session } = setup({ requiresSession: true, pages: [records("1")] });
      const result = await facade.search("stub", { keywords: "java", maxResults: 10 }, state);
      expect(factory.opened[0].options).toEqual({ visible: false, storageState: state });
      expect(result).toMatchObject({ valid: true, storageState: session.state });
    });

    it("con la sesión vencida responde valid=false sin buscar", async () => {
      const { facade, session } = setup({ requiresSession: true, validSession: false, pages: [records("1")] });
      const result = await facade.search("stub", { keywords: "java", maxResults: 10 }, state);
      expect(result).toEqual({ valid: false, jobs: [], pagesVisited: 0 });
      expect(session.pages).toHaveLength(0);
      expect(session.closeCalls).toBe(1);
    });

    it("sin storageState falla con SESSION_REQUIRED antes de abrir el navegador", async () => {
      const { facade, factory } = setup({ requiresSession: true, pages: [] });
      await expect(facade.search("stub", { keywords: "java", maxResults: 10 })).rejects.toMatchObject({ code: "SESSION_REQUIRED" });
      expect(factory.opened).toHaveLength(0);
    });
  });

  it("si el portal no abre (DNS, sin red) falla con PORTAL_UNREACHABLE y cierra el contexto", async () => {
    const { facade, session } = setup({ pages: [records("1")] });
    session.site.redirects = undefined;
    const original = session.newPage.bind(session);
    session.newPage = async () => {
      const page = await original();
      page.goto = async () => {
        throw new Error("page.goto: net::ERR_NAME_NOT_RESOLVED at https://stub.example\nCall log: ...");
      };
      return page;
    };
    await expect(facade.search("stub", { keywords: "java", maxResults: 10 })).rejects.toMatchObject({
      code: "PORTAL_UNREACHABLE",
      message: "No se pudo abrir Stub: net::ERR_NAME_NOT_RESOLVED at https://stub.example",
    });
    expect(session.closeCalls).toBe(1);
  });

  it("un portal desconocido falla con UNKNOWN_PORTAL", async () => {
    const { facade } = setup({ pages: [] });
    await expect(facade.search("nope", { keywords: "java", maxResults: 10 })).rejects.toBeInstanceOf(ScraperError);
  });
});

describe("normalizeCriteria", () => {
  it("acota maxResults, limpia el texto y valida los días", () => {
    expect(normalizeCriteria({ keywords: "  java ", maxResults: 999, postedWithinDays: 7 }, 200)).toEqual({
      keywords: "java",
      location: undefined,
      maxResults: 200,
      postedWithinDays: 7,
    });
    expect(normalizeCriteria({ keywords: "java", postedWithinDays: 90 }, 200)).toEqual({ keywords: "java", location: undefined, maxResults: 25 });
    expect(normalizeCriteria({ keywords: "java", remote: true }, 200)).toMatchObject({ remote: true });
    expect(normalizeCriteria({ keywords: "java", remote: "si" as unknown as boolean }, 200)).not.toHaveProperty("remote");
    expect(() => normalizeCriteria({ keywords: "j" }, 200)).toThrow(ScraperError);
  });
});
