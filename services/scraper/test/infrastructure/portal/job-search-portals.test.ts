import { describe, expect, it } from "vitest";
import { ComputrabajoJobSearch } from "../../../src/infrastructure/portal/computrabajo/computrabajo-job-search";
import { slug } from "../../../src/infrastructure/portal/computrabajo/computrabajo.selectors";
import { LinkedInJobSearch } from "../../../src/infrastructure/portal/linkedin/linkedin-job-search";
import { describeJobSearchPortalContract } from "./job-search-portal.contract";

/** Card de /jobs/search-results (DOM capturado el 2026-10-24), tal como la lee extractAll. */
const linkedInCard = {
  componentKey: "job-card-component-ref-4012345678",
  title: "Backend Developer",
  company: "Acme",
  location: "Bogotá, Colombia (Remoto)",
  text: "Backend Developer Acme Bogotá, Colombia (Remoto) Evaluando solicitudes Publicado hace 2 díashace 2 días · Solicitud sencilla",
};

/** Card real de co.computrabajo.com (2026-10-16), tal como la lee extractAll. */
const computrabajoCard = {
  id: "852FACA15F784A4E61373E686DCF3405",
  title: "Desarrollador Junior",
  href: "/ofertas-de-trabajo/oferta-de-trabajo-de-desarrollador-junior-en-bogota-dc-852FACA15F784A4E61373E686DCF3405#lc=ListOffers-Score3-0",
  company: "PHARI SAS",
  location: "Bogotá, D.C., Bogotá, D.C.",
  posted: "Hace 23 horas",
  salary: "$ 2.200.000,00 (Mensual)",
};

describeJobSearchPortalContract("LinkedIn", () => new LinkedInJobSearch(), linkedInCard);
describeJobSearchPortalContract("Computrabajo", () => new ComputrabajoJobSearch("co"), computrabajoCard);

describe("LinkedInJobSearch", () => {
  const portal = new LinkedInJobSearch();

  it("exige sesión y pagina con start=25·n", () => {
    expect(portal.requiresSession).toBe(true);
    const url = new URL(portal.searchUrl({ keywords: "java", location: "Bogotá", maxResults: 10, postedWithinDays: 7 }, 2));
    expect(url.pathname).toBe("/jobs/search/");
    expect(url.searchParams.get("keywords")).toBe("java");
    expect(url.searchParams.get("start")).toBe("50");
    expect(url.searchParams.get("f_TPR")).toBe("r604800");
    expect(url.searchParams.has("f_WT")).toBe(false);
  });

  it("filtra solo remotas con f_WT=2", () => {
    const url = new URL(portal.searchUrl({ keywords: "java", maxResults: 10, remote: true }, 0));
    expect(url.searchParams.get("f_WT")).toBe("2");
  });

  it("normaliza la card: id del componentkey, URL canónica, fecha del texto y Easy Apply", () => {
    expect(portal.toCard(linkedInCard)).toEqual({
      externalId: "4012345678",
      url: "https://www.linkedin.com/jobs/view/4012345678/",
      title: "Backend Developer",
      company: "Acme",
      location: "Bogotá, Colombia (Remoto)",
      postedText: "hace 2 días",
      salaryText: null,
      quickApply: true,
    });
  });

  it("quita la insignia de verificación y el título repetido pegado (caso real del 2026-10-16)", () => {
    const raw = "Desarrollador Full stack (Java/ React)Desarrollador Full stack (Java/ React) con verificación";
    expect(portal.toCard({ ...linkedInCard, title: raw })?.title).toBe("Desarrollador Full stack (Java/ React)");
  });

  it("descarta cards perezosas sin contenido o sin id y lee la fecha en inglés", () => {
    expect(portal.toCard({ componentKey: "job-card-component-ref-4012345678", title: null, company: null })).toBeNull();
    expect(portal.toCard({ ...linkedInCard, componentKey: "auto-component-x" })).toBeNull();
    const english = portal.toCard({ ...linkedInCard, text: "Acme · Posted 3 weeks ago · Easy Apply" });
    expect(english?.postedText).toBe("3 weeks ago");
    expect(english?.quickApply).toBe(true);
  });
});

describe("ComputrabajoJobSearch", () => {
  const portal = new ComputrabajoJobSearch("co");

  it("no exige sesión y arma la URL por país, palabras y lugar", () => {
    expect(portal.requiresSession).toBe(false);
    expect(portal.searchUrl({ keywords: "Desarrollador Java", location: "Bogotá D.C.", maxResults: 5 }, 0)).toBe(
      "https://co.computrabajo.com/trabajo-de-desarrollador-java-en-bogota-d-c",
    );
    expect(portal.searchUrl({ keywords: "java", maxResults: 5 }, 2)).toBe("https://co.computrabajo.com/trabajo-de-java?p=3");
    expect(new ComputrabajoJobSearch("mx").searchUrl({ keywords: "java", maxResults: 5 }, 0)).toContain("mx.computrabajo.com");
  });

  it("remoto ignora el lugar y la fecha usa el menor pubdate que la cubre (validado el 2026-10-23)", () => {
    expect(portal.searchUrl({ keywords: "java", location: "Bogotá", maxResults: 5, remote: true, postedWithinDays: 5 }, 1)).toBe(
      "https://co.computrabajo.com/trabajo-de-java-en-remoto?pubdate=7&p=2",
    );
    expect(portal.searchUrl({ keywords: "java", maxResults: 5, postedWithinDays: 1 }, 0)).toBe(
      "https://co.computrabajo.com/trabajo-de-java?pubdate=1",
    );
    expect(portal.searchUrl({ keywords: "java", maxResults: 5, postedWithinDays: 30 }, 0)).toContain("pubdate=30");
  });

  it("normaliza la card y descarta enlaces externos", () => {
    expect(portal.toCard(computrabajoCard)).toEqual({
      externalId: "852FACA15F784A4E61373E686DCF3405",
      url: "https://co.computrabajo.com/ofertas-de-trabajo/oferta-de-trabajo-de-desarrollador-junior-en-bogota-dc-852FACA15F784A4E61373E686DCF3405",
      title: "Desarrollador Junior",
      company: "PHARI SAS",
      location: "Bogotá, D.C., Bogotá, D.C.",
      postedText: "Hace 23 horas",
      salaryText: "$ 2.200.000,00 (Mensual)",
      quickApply: false,
    });
    expect(portal.toCard({ ...computrabajoCard, href: "https://ads.example/x" })).toBeNull();
    expect(portal.toCard({ ...computrabajoCard, company: null })?.company).toBe("Empresa confidencial");
  });

  it("rechaza países inválidos", () => {
    expect(() => new ComputrabajoJobSearch("colombia")).toThrow();
  });

  it("slug quita tildes y signos", () => {
    expect(slug("  Diseñador UX/UI Sénior ")).toBe("disenador-ux-ui-senior");
  });
});
