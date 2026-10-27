import { Module } from "@nestjs/common";
import { APP_FILTER, APP_GUARD } from "@nestjs/core";
import { BrowserModule } from "./infrastructure/config/browser.module";
import { PortalsModule } from "./infrastructure/config/portals.module";
import { ServicesModule } from "./infrastructure/config/services.module";
import { ServiceTokenGuard } from "./infrastructure/security/service-token.guard";
import { ScraperExceptionHandler } from "./api/scraper-exception-handler";
import { HealthController } from "./api/health.controller";
import { JobSearchController } from "./api/job-search.controller";
import { LinkedInSessionsController } from "./api/linkedin-sessions.controller";
import { PortalSessionsController } from "./api/portal-sessions.controller";
import { SessionsHttpHandler } from "./api/sessions-http-handler";

@Module({
  imports: [BrowserModule, PortalsModule, ServicesModule],
  controllers: [HealthController, PortalSessionsController, LinkedInSessionsController, JobSearchController],
  providers: [
    SessionsHttpHandler,
    { provide: APP_GUARD, useClass: ServiceTokenGuard },
    { provide: APP_FILTER, useClass: ScraperExceptionHandler },
  ],
})
export class AppModule {}
