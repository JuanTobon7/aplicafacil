import { Page } from 'puppeteer';
import { LinkedInSearchParams } from '../../../dto/params.lindkln.search';

export abstract class JobSearchComponent {
  abstract searchJobs(
    page: Page,
    params: LinkedInSearchParams,
  ): Promise<string[]>;
}