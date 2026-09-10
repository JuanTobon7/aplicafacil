import { Browser, Page } from 'puppeteer';

export abstract class BrowserManager {
  abstract launch(): Promise<Browser>;
  abstract newPage(browser: Browser): Promise<Page>;
  abstract close(browser: Browser): Promise<void>;
}