import { Inject, Injectable, Logger } from '@nestjs/common';
import { Browser, Page } from 'puppeteer';
import { ScrapingLinkldnService } from '../../contract/scraping.linkldn.service';
import { LinkedInSearchParams } from '../../../dto/params.lindkln.search';
import { JobPostingDto } from 'src/jobs/dto/req/job..osting.dto';
import { BrowserManager } from '../../../components/browser-manager/contract/browser.manager';
import { LinkedInLoginComponent } from '../../../components/login/contract/linkedin.login.component';
import { JobSearchComponent } from '../../../components/job-search/contract/job.search.component';
import { JobDetailExtractorComponent } from '../../../components/job-detail/contract/job.detail.extractor.component';
import { EasyApplyComponent } from '../../../components/easy-apply/contract/easy.apply.component';

@Injectable()
export class ScrapingLinkldnServiceImpl implements ScrapingLinkldnService {
  private readonly logger = new Logger(ScrapingLinkldnServiceImpl.name);
  private browser: Browser | null = null;
  private page: Page | null = null;

  constructor(
    @Inject(BrowserManager)
    private readonly browserManager: BrowserManager,
    @Inject(LinkedInLoginComponent)
    private readonly loginComponent: LinkedInLoginComponent,
    @Inject(JobSearchComponent)
    private readonly jobSearchComponent: JobSearchComponent,
    @Inject(JobDetailExtractorComponent)
    private readonly jobDetailExtractor: JobDetailExtractorComponent,
    @Inject(EasyApplyComponent)
    private readonly easyApplyComponent: EasyApplyComponent,
  ) {}

  /**
   * Abre el perfil de LinkedIn iniciando sesión con las credenciales dadas.
   */
  async openLinkdlnProfile({
    email,
    password,
  }: {
    email: string;
    password: string;
  }): Promise<void> {
    this.logger.log('Opening LinkedIn profile...');

    if (!this.browser) {
      this.browser = await this.browserManager.launch();
    }

    this.page = await this.browserManager.newPage(this.browser);

    await this.loginComponent.login(this.page, { email, password });

    this.logger.log('LinkedIn profile opened successfully.');
  }

  /**
   * Obtiene la lista de vacantes a postularse navegando a la página de
   * búsqueda de empleos, aplicando los filtros y extrayendo las vacantes.
   */
  async getJobsToApply(params: LinkedInSearchParams): Promise<JobPostingDto[]> {
    this.logger.log('Getting jobs to apply...');

    const page = this.requirePage();

    const jobLinks = await this.jobSearchComponent.searchJobs(page, params);

    const jobs: JobPostingDto[] = [];
    for (const link of jobLinks) {
      const job = await this.jobDetailExtractor.extractJob(page, link);
      if (job) {
        jobs.push(job);
      }
    }

    return jobs;
  }

  /**
   * Busca una vacante específica por URL y extrae toda su información.
   */
  async searchJob(url: string): Promise<JobPostingDto | null> {
    this.logger.log(`Searching job: ${url}`);

    const page = this.requirePage();

    return this.jobDetailExtractor.extractJob(page, url);
  }

  /**
   * Resuelve el formulario de postulación y aplica a la vacante.
   */
  async resolveFillFormAndApply(job: JobPostingDto): Promise<void> {
    this.logger.log(`Resolving fill form and applying to: ${job.job.title}`);

    const page = this.requirePage();

    await this.easyApplyComponent.apply(page, job);
  }

  /**
   * Cierra el navegador.
   */
  async close(): Promise<void> {
    if (this.browser) {
      await this.browserManager.close(this.browser);
      this.browser = null;
      this.page = null;
    }
  }

  /**
   * Retorna la página actual o lanza un error si no está abierta.
   */
  private requirePage(): Page {
    if (!this.page) {
      throw new Error(
        'LinkedIn page is not open. Call openLinkdlnProfile first.',
      );
    }
    return this.page;
  }
}