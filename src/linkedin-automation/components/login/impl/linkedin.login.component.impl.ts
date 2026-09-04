import { Injectable, Logger } from '@nestjs/common';
import { Page } from 'puppeteer';
import {
  LinkedInCredentials,
  LinkedInLoginComponent,
} from '../contract/linkedin.login.component';

@Injectable()
export class LinkedInLoginComponentImpl implements LinkedInLoginComponent {
  private readonly logger = new Logger(LinkedInLoginComponentImpl.name);

  private readonly LOGIN_URL = 'https://www.linkedin.com/login';

  async login(page: Page, credentials: LinkedInCredentials): Promise<void> {
    this.logger.log('Logging into LinkedIn...');

    await page.goto(this.LOGIN_URL, {
      waitUntil: 'networkidle2',
      timeout: 60_000,
    });

    // Esperar a que carguen los campos de login
    await page.waitForSelector('#username', { timeout: 30_000 });
    await page.waitForSelector('#password', { timeout: 30_000 });

    await page.type('#username', credentials.email, { delay: 50 });
    await page.type('#password', credentials.password, { delay: 50 });

    await Promise.all([
      page.waitForNavigation({ waitUntil: 'networkidle2', timeout: 60_000 }),
      page.click('button[type="submit"]'),
    ]);

    this.logger.log('LinkedIn login successful.');
  }
}