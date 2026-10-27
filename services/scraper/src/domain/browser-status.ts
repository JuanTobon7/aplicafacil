export type BrowserState = "INSTALLING" | "READY" | "FAILED";

export type BrowserStatus = { browser: BrowserState; detail?: string };
