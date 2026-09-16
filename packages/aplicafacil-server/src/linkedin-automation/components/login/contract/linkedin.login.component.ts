import { Page } from 'puppeteer';

export interface LinkedInCredentials {
  email: string;
  password: string;
}

export abstract class LinkedInLoginComponent {
  abstract login(page: Page, credentials: LinkedInCredentials): Promise<void>;
}