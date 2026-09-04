import { Page } from 'puppeteer';
import { JobPostingDto } from 'src/jobs/dto/req/job..osting.dto';

export abstract class JobDetailExtractorComponent {
  abstract extractJob(page: Page, url: string): Promise<JobPostingDto | null>;
}