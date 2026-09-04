import { Injectable, Logger } from '@nestjs/common';
import puppeteer, { Browser, Page } from 'puppeteer';
import { BrowserManager } from '../contract/browser.manager';

@Injectable()
export class BrowserManagerImpl implements BrowserManager {
  private readonly logger = new Logger(BrowserManagerImpl.name);

  async launch(): Promise<Browser> {
    this.logger.log('Launching browser...');
    return puppeteer.launch({
      headless: false,
      defaultViewport: { width: 1280, height: 800 },
      args: ['--no-sandbox', '--disable-setuid-sandbox'],
    });
  }

  async newPage(browser: Browser): Promise<Page> {
    const page = await browser.newPage();
    // Set a realistic user agent to avoid bot detection
    await page
      .setUserAgent({
        userAgent:
          'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        platform: 'Windows',
      })
      .catch(() => undefined);
    return page;
  }

  async close(browser: Browser): Promise<void> {
    this.logger.log('Closing browser...');
    await browser.close();
  }
}