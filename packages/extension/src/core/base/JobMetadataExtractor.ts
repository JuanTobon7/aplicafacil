import { JobMetadata } from "../types/JobMetadataExtractor";


export interface JobMetadataExtractor {
  /** Indica si este extractor puede manejar la página actual */
  canExtract(): boolean;
  /** Extrae los metadatos y los devuelve */
  extract(): JobMetadata;
}

/** Clase base con utilidades comunes */
export abstract class BaseMetadataExtractor implements JobMetadataExtractor {
  abstract canExtract(): boolean;
  abstract extract(): JobMetadata;

  /** Limpia texto eliminando espacios extra y saltos de línea */
  protected cleanText(text: string | null | undefined): string {
    return text?.replace(/\s+/g, ' ').trim() || "";
  }

  /** Obtiene texto de un elemento por selector */
  protected getText(selector: string, parent: Document | Element = document): string {
    const el = parent.querySelector(selector);
    return this.cleanText(el?.textContent);
  }
}