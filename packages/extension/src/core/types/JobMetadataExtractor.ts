// core/types/job-metadata.ts
export interface JobMetadata {
  title?: string;
  company?: string;
  location?: string;
  description?: string;
  postedDate?: string;
  employmentType?: string;       // "full-time", "contract", etc.
  workplaceType?: string;        // "remote", "hybrid", "on-site"
  salary?: string;
  skills?: string[];
  url?: string;
  // adicionales
  [key: string]: any;
}