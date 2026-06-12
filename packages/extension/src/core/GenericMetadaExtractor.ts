import { BaseMetadataExtractor, JobMetadataExtractor } from "./base/JobMetadataExtractor";
import { JobMetadata } from "./types/JobMetadataExtractor";

/**
 * Intenta extraer metadatos usando meta tags, Schema.org JSON-LD y selectores comunes.
 */
export class GenericMetadataExtractor extends BaseMetadataExtractor implements JobMetadataExtractor {
  canExtract(): boolean {
    // Siempre puede intentar extraer, pero se usará como fallback si no hay otro específico
    return true;
  }

  extract(): JobMetadata {
    const metadata: JobMetadata = {};

    // Meta tags comunes
    metadata.title = this.getMetaContent("og:title") || document.title;
    metadata.description = this.getMetaContent("og:description") || this.getMetaContent("description");
    metadata.url = this.getMetaContent("og:url") || location.href;

    // Schema.org JSON-LD (estructurado)
    const jsonLd = this.extractJsonLd();
    if (jsonLd) {
      metadata.title = metadata.title || jsonLd.title || jsonLd.name;
      metadata.company = jsonLd.hiringOrganization?.name || jsonLd.employer?.name;
      metadata.location = jsonLd.jobLocation?.address?.addressLocality || jsonLd.location;
      metadata.description = metadata.description || jsonLd.description;
      metadata.postedDate = jsonLd.datePosted;
      metadata.employmentType = jsonLd.employmentType;
      metadata.salary = jsonLd.baseSalary?.value?.value;
    }

    // Selectores comunes en formularios de empleo
    if (!metadata.company) {
      metadata.company = this.getText('[itemprop="hiringOrganization"] [itemprop="name"], .company-name');
    }
    if (!metadata.location) {
      metadata.location = this.getText('[itemprop="jobLocation"] [itemprop="addressLocality"], .location');
    }

    return metadata;
  }

  private getMetaContent(name: string): string | undefined {
    const meta = document.querySelector(`meta[name="${name}"], meta[property="${name}"]`);
    return meta?.getAttribute("content") || undefined;
  }

  private extractJsonLd(): any {
    const scripts = document.querySelectorAll('script[type="application/ld+json"]');
    for (const script of scripts) {
      try {
        const data = JSON.parse(script.textContent || "");
        if (data["@type"] === "JobPosting" || data["@type"]?.includes("JobPosting")) {
          return data;
        }
        // Algunos sitios anidan el JobPosting dentro de un array o un objeto principal
        if (data["@graph"] && Array.isArray(data["@graph"])) {
          const job = data["@graph"].find((item: any) => item["@type"] === "JobPosting");
          if (job) return job;
        }
      } catch (e) {
        // ignorar JSON inválido
      }
    }
    return null;
  }
}