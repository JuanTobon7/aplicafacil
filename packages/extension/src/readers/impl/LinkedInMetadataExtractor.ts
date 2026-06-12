import { BaseMetadataExtractor, JobMetadataExtractor } from "../../core/base/JobMetadataExtractor";
import { JobMetadata } from "../../core/types/JobMetadataExtractor";

export class LinkedInMetadataExtractor extends BaseMetadataExtractor implements JobMetadataExtractor {
    canExtract(): boolean {
        const isLinkedin = location.hostname.includes("linkedin.com");
        const hasJobs = location.href.includes("/jobs/");
        const result = isLinkedin && hasJobs;
        console.log(`[LinkedInMetadataExtractor] canExtract: ${result} (hostname: ${location.hostname}, href: ${location.href})`);
        return result;
    }

  extract(): JobMetadata {
    const metadata: JobMetadata = {};

    // Título del puesto (varias clases posibles)
    metadata.title = this.getText(
      '.job-details-jobs-unified-top-card__job-title h1, ' +
      'h1[class*="job-title"], ' +
      '[data-job-title]'
    );

    // Nombre de la empresa
    metadata.company = this.getText(
      '.job-details-jobs-unified-top-card__company-name a, ' +
      '[data-company-name]'
    );

    // Ubicación - primer span dentro del contenedor de descripción primaria
    const locationSpan = document.querySelector(
      '.job-details-jobs-unified-top-card__primary-description-container span:first-child'
    );
    metadata.location = this.cleanText(locationSpan?.textContent);

    // Fecha de publicación (tercer span aproximadamente)
    const dateSpan = document.querySelector(
      '.job-details-jobs-unified-top-card__primary-description-container span:nth-child(3)'
    );
    metadata.postedDate = this.cleanText(dateSpan?.textContent);

    // Descripción completa
    metadata.description = this.getText('#job-details, .jobs-description-content__text');

    // Tipo de empleo (full-time, part-time, etc.) desde los badges
    const employmentBadges = document.querySelectorAll('.job-details-fit-level-preferences button');
    for (const badge of employmentBadges) {
      const text = this.cleanText(badge.textContent);
      if (text.includes("Jornada completa")) metadata.employmentType = "full-time";
      else if (text.includes("Jornada parcial")) metadata.employmentType = "part-time";
      else if (text.includes("Contrato")) metadata.employmentType = "contract";
      if (text.includes("Híbrido")) metadata.workplaceType = "hybrid";
      else if (text.includes("Remoto")) metadata.workplaceType = "remote";
      else if (text.includes("Presencial")) metadata.workplaceType = "on-site";
    }

    // Skills desde el badge de coincidencia de aptitudes
    const skillsMatch = document.querySelector('.job-details-fit-level-preferences button:last-child');
    if (skillsMatch) {
      const skillsText = this.cleanText(skillsMatch.textContent);
      const match = skillsText.match(/(\d+) de (\d+) coincidencias de aptitudes/);
      if (match) {
        metadata.skillsMatch = {
          matched: parseInt(match[1]),
          total: parseInt(match[2])
        };
      }
    }

    // URL de la oferta
    metadata.url = location.href;

    return metadata;
  }
}