import { Controller, Get, Inject } from "@nestjs/common";
import {
  BROWSER_STATUS_PROVIDER,
  type BrowserStatusProvider,
} from "../application/port/out/browser-status-provider";
import { Public } from "../infrastructure/security/public.decorator";

@Controller("health")
export class HealthController {
  constructor(@Inject(BROWSER_STATUS_PROVIDER) private readonly browser: BrowserStatusProvider) {}

  @Get()
  @Public()
  health() {
    return this.browser.status();
  }
}
