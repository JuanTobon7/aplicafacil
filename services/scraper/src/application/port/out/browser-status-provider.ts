import type { BrowserStatus } from "../../../domain/browser-status";

export interface BrowserStatusProvider {
  status(): BrowserStatus;
}

export const BROWSER_STATUS_PROVIDER = Symbol("BrowserStatusProvider");
