import { Page } from 'puppeteer';
import { JobPostingDto } from 'src/jobs/dto/req/job..osting.dto';

export abstract class EasyApplyComponent {
  abstract apply(page: Page, job: JobPostingDto): Promise<void>;
}