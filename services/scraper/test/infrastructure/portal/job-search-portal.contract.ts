import { describe, expect, it } from "vitest";
import type { ExtractedRecord } from "../../../src/application/port/out/browser-page";
import type { JobSearchPortal } from "../../../src/application/port/out/job-search-portal";

/**
 * Suite de contrato (Liskov) de JobSearchPortal: todo portal de búsqueda debe pasarla para que JobSearchFacade
 * lo use sin cambios. `validRecord` es una card completa tal como la leería extractAll en ese portal.
 */
export function describeJobSearchPortalContract(name: string, create: () => JobSearchPortal, validRecord: ExtractedRecord) {
  describe(`${name} cumple el contrato de JobSearchPortal`, () => {
    const criteria = { keywords: "Desarrollador Java Sénior", location: "Bogotá", maxResults: 50 };

    it("tiene id para la URL, nombre visible y tamaño de página", () => {
      const portal = create();
      expect(portal.id).toMatch(/^[a-z0-9-]+$/);
      expect(portal.displayName.trim()).not.toBe("");
      expect(portal.pageSize).toBeGreaterThan(0);
    });

    it("arma URLs https propias que cambian con la página", () => {
      const portal = create();
      const first = new URL(portal.searchUrl(criteria, 0));
      const second = new URL(portal.searchUrl(criteria, 1));
      expect(first.protocol).toBe("https:");
      expect(second.host).toBe(first.host);
      expect(second.toString()).not.toBe(first.toString());
    });

    it("declara selectores para el listado y sus cards", () => {
      const { cards } = create();
      expect(cards.readyWhen.trim()).not.toBe("");
      expect(cards.item.trim()).not.toBe("");
      expect(Object.keys(cards.fields).length).toBeGreaterThan(0);
    });

    it("convierte una card completa en una oferta normalizada", () => {
      const card = create().toCard(validRecord);
      expect(card).not.toBeNull();
      expect(card).toMatchObject({
        externalId: expect.any(String),
        title: expect.any(String),
        company: expect.any(String),
        quickApply: expect.any(Boolean),
      });
      expect(new URL(card!.url).protocol).toBe("https:");
    });

    it("descarta cards vacías o incompletas devolviendo null, sin lanzar", () => {
      const portal = create();
      const empty = Object.fromEntries(Object.keys(portal.cards.fields).map((k) => [k, null]));
      expect(portal.toCard(empty)).toBeNull();
      expect(portal.toCard({ ...validRecord, title: null })).toBeNull();
    });
  });
}
