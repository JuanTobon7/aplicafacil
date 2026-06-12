import { JobMetadataExtractor } from "../core/base/JobMetadataExtractor";
import { GenericMetadataExtractor } from "../core/GenericMetadaExtractor";
import { LinkedInMetadataExtractor } from "./impl/LinkedInMetadataExtractor";

export class MetadataExtractorFactory {
  private static readonly knownReaders: Record<string, JobMetadataExtractor> = {
    linkedin: new LinkedInMetadataExtractor(),
    generic: new GenericMetadataExtractor(),
  };

  static getExtractor(name: string): JobMetadataExtractor {
    console.log("[MetadataExtractorFactory] Checking extractors...");
    const extractor = this.knownReaders[name];
    if (extractor) {
      console.log(`[MetadataExtractorFactory] Selected: ${extractor.constructor.name}`);
      return extractor;
    }
    console.log("[MetadataExtractorFactory] No extractor found, returning GenericMetadataExtractor (fallback)");
    return new GenericMetadataExtractor();
  }
}