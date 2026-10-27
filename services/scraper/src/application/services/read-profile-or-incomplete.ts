import { incompleteProfile, type ProfileSnapshot } from "../../domain/profile-snapshot";
import type { AppLogger } from "../port/out/app-logger";
import type { BrowserSession } from "../port/out/browser-session";
import type { PortalProfileReader } from "../port/out/portal-profile-reader";

/** Un fallo parcial no impide crear el usuario: el perfil queda incompleto para reintentar. */
export async function readProfileOrIncomplete(
  reader: PortalProfileReader,
  session: BrowserSession,
  logger: AppLogger,
): Promise<ProfileSnapshot> {
  try {
    return await reader.read(session);
  } catch (error) {
    logger.warn(`Lectura parcial del perfil: ${(error as Error).name}`);
    return incompleteProfile(["all"]);
  }
}
