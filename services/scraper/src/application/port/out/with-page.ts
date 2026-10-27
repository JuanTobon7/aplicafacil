import type { BrowserPage } from "./browser-page";
import type { SessionPages } from "./browser-session";

/** Abre una pestaña, ejecuta `action` y la cierra siempre. */
export async function withPage<T>(session: SessionPages, action: (page: BrowserPage) => Promise<T>): Promise<T> {
  const page = await session.newPage();
  try {
    return await action(page);
  } finally {
    await page.close().catch(() => undefined);
  }
}
