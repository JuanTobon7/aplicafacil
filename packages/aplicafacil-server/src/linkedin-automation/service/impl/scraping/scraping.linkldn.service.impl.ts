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
import { CaptchaDetector } from '../../../components/captcha/contract/captcha.detector';

@Injectable()
export class ScrapingLinkldnServiceImpl implements ScrapingLinkldnService {
  private readonly logger = new Logger(ScrapingLinkldnServiceImpl.name);
  private browser: Browser | null = null;
  private page: Page | null = null;
  /** Pestaña separada para búsqueda (la principal es exclusiva de la cola). */
  private searchPage: Page | null = null;

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
    @Inject(CaptchaDetector)
    private readonly captchaDetector: CaptchaDetector,
  ) {}

  /**
   * Abre el perfil de LinkedIn iniciando sesión con las credenciales dadas.
   *
   * Idempotente: si el navegador ya está abierto y logueado, no hace nada.
   * Esto permite que la cola reutilice la misma sesión sin re-loguear.
   */
  async openLinkdlnProfile({
    email,
    password,
  }: {
    email: string;
    password: string;
  }): Promise<void> {
    if (this.page) {
      this.logger.log('LinkedIn profile already open. Reusing session.');
      return;
    }

    this.logger.log('Opening LinkedIn profile...');

    if (!this.browser) {
      this.browser = await this.browserManager.launch();
    }

    this.page = await this.browserManager.newPage(this.browser);

    await this.loginComponent.login(this.page, { email, password });

    // Tras el login, comprobamos que LinkedIn no haya interpuesto un
    // CAPTCHA (el guard del BrowserManager ya lo pausó esperando a que
    // un humano lo resuelva en el navegador visible).
    await this.assertNoCaptcha(this.page);

    this.logger.log('LinkedIn profile opened successfully.');
  }

  /**
   * Lanza `CaptchaDetectedError` si la página actual es un challenge de
   * seguridad de LinkedIn (CAPTCHA). Se delega en el CaptchaDetector.
   */
  async assertNoCaptcha(page: Page): Promise<void> {
    await this.captchaDetector.assertNoCaptcha(page);
  }

  /**
   * Espera (con timeout) a que un humano resuelva el CAPTCHA en el
   * navegador visible. Se delega en el CaptchaDetector.
   */
  async waitForCaptchaResolution(
    page: Page,
    timeoutMs?: number,
  ): Promise<boolean> {
    return this.captchaDetector.waitForCaptchaResolution(page, timeoutMs);
  }

  /**
   * Obtiene la lista de vacantes a postularse navegando a la página de
   * búsqueda de empleos, aplicando los filtros y extrayendo las vacantes.
   *
   * SIEMPRE usa una pestaña separada (searchPage) para no interferir con
   * la página principal que usa la cola para postular. Así el cron de
   * búsqueda nunca pisa una postulación en curso.
   *
   * El flujo navega ENTRE las tarjetas de la lista (click en cada una para
   * cargar el detalle en el panel derecho) en lugar de hacer page.goto()
   * por cada vacante, que es más lento y propenso a bloqueos.
   */
  async getJobsToApply(params: LinkedInSearchParams): Promise<JobPostingDto[]> {
    this.logger.log('Getting jobs to apply...');

    const page = await this.getSearchPage();

    // Si LinkedIn interpuso un CAPTCHA en la pestaña de búsqueda, abortamos
    // de forma controlada (el guard del BrowserManager ya lo pausó esperando
    // a que un humano lo resuelva en el navegador visible).
    await this.assertNoCaptcha(page);

    const jobLinks = await this.jobSearchComponent.searchJobs(page, params);

    const jobs: JobPostingDto[] = [];
    for (const link of jobLinks) {
      const jobId = this.extractJobId(link);
      if (!jobId) {
        this.logger.warn(`Could not extract jobId from ${link}`);
        continue;
      }

      try {
        // Navegar a la tarjeta: click para cargar el detalle en el panel derecho
        await this.jobSearchComponent.clickJobCard(page, jobId);

        // Leer el detalle del panel derecho (sin navegar)
        const job = await this.jobDetailExtractor.extractJobFromPanel(
          page,
          link,
        );
        if (job) {
          jobs.push(job);
        }
      } catch (error) {
        this.logger.warn(`Could not process job ${link}: ${error}`);
      }
    }

    return jobs;
  }

  /**
   * Extrae el jobId numérico de una URL de vacante de LinkedIn.
   */
  private extractJobId(url: string): string | null {
    const match = /\/jobs\/view\/(\d+)/.exec(url);
    return match?.[1] ?? null;
  }

  /**
   * Busca una vacante específica por URL y extrae toda su información.
   */
  async searchJob(url: string): Promise<JobPostingDto | null> {
    this.logger.log(`Searching job: ${url}`);

    const page = this.requirePage();

    // Si LinkedIn interpuso un CAPTCHA, abortamos de forma controlada.
    await this.assertNoCaptcha(page);

    return this.jobDetailExtractor.extractJob(page, url);
  }

  /**
   * Resuelve el formulario de postulación y aplica a la vacante.
   * Marca la página principal como "en uso" para que el cron de búsqueda
   * abra una pestaña separada y no pise la postulación en curso.
   */
  async resolveFillFormAndApply(job: JobPostingDto): Promise<void> {
    this.logger.log(`Resolving fill form and applying to: ${job.job.title}`);

    const page = this.requirePage();

    // Si LinkedIn interpuso un CAPTCHA al abrir la vacante, abortamos de
    // forma controlada (el guard del BrowserManager ya lo pausó esperando
    // a que un humano lo resuelva en el navegador visible).
    await this.assertNoCaptcha(page);

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
      this.searchPage = null;
    }
  }

  /**
   * Retorna la página a usar para búsqueda.
   * SIEMPRE abre/usa una pestaña separada para no interferir con la cola.
   */
  private async getSearchPage(): Promise<Page> {
    if (!this.browser) {
      throw new Error(
        'LinkedIn browser is not open. Call openLinkdlnProfile first.',
      );
    }

    if (!this.searchPage) {
      this.logger.log('Opening new tab for job search...');
      this.searchPage = await this.browserManager.newPage(this.browser);
    }
    return this.searchPage;
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