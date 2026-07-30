import { IWriter } from "./base/IWriter";
import { GenericWriter } from "./impl/GenericWriter";

// ------------------------------------------------------------------
// Factory de writers: selecciona la implementación según el hostname,
// igual que ReaderFactory.getReader().
// ------------------------------------------------------------------
export class WriterFactory {
  private static writers: Record<string, () => IWriter> = {
    // "linkedin.com": () => new LinkedInWriter(),
  };

  static getWriter(hostname: string): IWriter {
    console.log("[AutoApply] WriterFactory resolviendo writer para:", hostname);

    const matchedKey = Object.keys(this.writers).find((domain) => hostname.includes(domain));

    if (matchedKey) {
      console.log("[AutoApply] Writer específico encontrado:", matchedKey);
      return this.writers[matchedKey]();
    }

    console.log("[AutoApply] Usando GenericWriter (fallback)");
    return new GenericWriter();
  }
}