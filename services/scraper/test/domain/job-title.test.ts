import { describe, expect, it } from "vitest";
import { InvalidJobCardError, jobTitle, MAX_TITLE_LENGTH } from "../../src/domain/job-title";

describe("jobTitle (regla de dominio del título)", () => {
  it.each([
    // Casos reales guardados el 2026-10-16: span visible + span para lectores de pantalla, sin espacio entre ellos
    ["Desarrollador Full stack (Java/ React)Desarrollador Full stack (Java/ React)", "Desarrollador Full stack (Java/ React)"],
    ["Desarrollador de Automatismos PLC y HMIDesarrollador de Automatismos PLC y HMI", "Desarrollador de Automatismos PLC y HMI"],
    ["Backend Developer Backend Developer", "Backend Developer"],
    ["  Backend\n   Developer\tBackend Developer ", "Backend Developer"],
  ])("colapsa el título repetido: %s", (raw, expected) => {
    expect(jobTitle(raw)).toBe(expected);
  });

  it("deja igual un título normal, aunque repita palabras", () => {
    expect(jobTitle("Senior Data Developer, Colombia")).toBe("Senior Data Developer, Colombia");
    expect(jobTitle("Desarrollador Java / Desarrollador Kotlin")).toBe("Desarrollador Java / Desarrollador Kotlin");
  });

  it("rechaza un título vacío o demasiado largo con InvalidJobCardError", () => {
    expect(() => jobTitle("   ")).toThrow(InvalidJobCardError);
    expect(() => jobTitle("x".repeat(MAX_TITLE_LENGTH + 1))).toThrow(/máximo 300/);
  });
});
