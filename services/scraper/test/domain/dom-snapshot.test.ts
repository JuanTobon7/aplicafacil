import { describe, expect, it } from "vitest";
import { sanitizeHtml } from "../../src/domain/dom-snapshot";

describe("sanitizeHtml", () => {
  it("vacía scripts y bloques de datos, pero deja la estructura que usan los selectores", () => {
    const html = `<main><script>window.token="abc"</script><code id="bpr">{"firstName":"Juan"}</code>
      <li data-occludable-job-id="4012345678" class="jobs-card"><a class="job-card-list__title--link">Backend</a></li></main>`;

    const out = sanitizeHtml(html);

    expect(out).not.toContain("abc");
    expect(out).not.toContain("firstName");
    expect(out).toContain('data-occludable-job-id="4012345678"');
    expect(out).toContain('class="job-card-list__title--link">Backend</a>');
  });

  it("oculta correos, identificadores del miembro, tokens, valores de formularios y fotos", () => {
    const html = `<p>ana.perez@example.com</p><a href="/in/x" data-urn="urn:li:fsd_profile:ACoAAB12cdEF">x</a>
      <input name="csrfToken" value="ajax:123"><meta name="csrf-token" content="x"><img src="https://media.licdn.com/foto.jpg">
      <span data-urn="urn:li:jobPosting:4012345678"></span>`;

    const out = sanitizeHtml(html);

    expect(out).not.toMatch(/ana\.perez|ACoAAB12cdEF|ajax:123|media\.licdn/);
    expect(out).toContain("urn:li:jobPosting:4012345678");
    expect(out).toContain('value="[oculto]"');
  });

  it("oculta los textos pedidos sin importar mayúsculas", () => {
    expect(sanitizeHtml("<h1>Juan Carlos Tobon</h1>", ["juan carlos tobon"])).toBe("<h1>[oculto]</h1>");
    expect(sanitizeHtml("<h1>Ana</h1>", ["  ", "An"])).toBe("<h1>Ana</h1>"); // textos muy cortos no se ocultan
  });
});
