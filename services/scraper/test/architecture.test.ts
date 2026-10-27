import { readdirSync, readFileSync } from "node:fs";
import { join, posix, relative, sep } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * Reglas hexagonales (equivalente al ArchitectureTest de los servicios Spring):
 * domain ← application ← infrastructure / api. Solo infrastructure y api conocen Nest y Playwright.
 */
const SRC = join(__dirname, "..", "src");

type Source = { file: string; layer: string; imports: string[] };

function sources(dir = SRC): Source[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) return sources(path);
    if (!entry.name.endsWith(".ts")) return [];
    const file = relative(SRC, path).split(sep).join("/");
    const imports = [...readFileSync(path, "utf8").matchAll(/(?:from|import)\s*\(?\s*"([^"]+)"/g)].map(([, spec]) =>
      spec.startsWith(".") ? posix.normalize(posix.join(posix.dirname(file), spec)) : spec,
    );
    return [{ file, layer: file.includes("/") ? file.split("/")[0] : "root", imports }];
  });
}

const all = sources();
const layer = (name: string) => all.filter((s) => s.layer === name);
const violations = (from: Source[], forbidden: (spec: string) => boolean) =>
  from.flatMap((s) => s.imports.filter(forbidden).map((spec) => `${s.file} → ${spec}`));

const isLayer = (...names: string[]) => (spec: string) => names.some((n) => spec === n || spec.startsWith(`${n}/`));
const isFramework = (spec: string) => /^(@nestjs\/|playwright|express|jose)/.test(spec);

describe("arquitectura hexagonal", () => {
  it("domain no depende de nadie", () => {
    expect(violations(layer("domain"), (s) => isLayer("application", "infrastructure", "api")(s) || isFramework(s))).toEqual([]);
  });

  it("application no depende de infrastructure, api ni frameworks", () => {
    expect(violations(layer("application"), (s) => isLayer("infrastructure", "api")(s) || isFramework(s))).toEqual([]);
  });

  it("application/port solo depende de domain, excepciones y otros puertos", () => {
    const ports = layer("application").filter((s) => s.file.startsWith("application/port/"));
    expect(violations(ports, isLayer("application/services"))).toEqual([]);
  });

  it("infrastructure no depende de api", () => {
    expect(violations(layer("infrastructure"), isLayer("api"))).toEqual([]);
  });

  it("api llega a la aplicación solo por puertos de entrada (no por services)", () => {
    expect(violations(layer("api"), isLayer("application/services", "infrastructure/browser"))).toEqual([]);
  });

  it("los portales no conocen el motor del navegador", () => {
    const portals = layer("infrastructure").filter((s) => s.file.startsWith("infrastructure/portal/"));
    expect(violations(portals, (s) => s === "playwright" || isLayer("infrastructure/browser")(s))).toEqual([]);
  });

  it("encuentra todas las capas", () => {
    expect(new Set(all.map((s) => s.layer))).toEqual(new Set(["root", "domain", "application", "infrastructure", "api"]));
  });
});
