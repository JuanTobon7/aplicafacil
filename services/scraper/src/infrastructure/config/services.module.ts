import { Logger, Module } from "@nestjs/common";
import { PORTAL_LOGIN_SERVICE } from "../../application/port/in/portal-login-service";
import { PORTAL_SESSION_SERVICE } from "../../application/port/in/portal-session-service";
import {
  BROWSER_SESSION_FACTORY,
  type BrowserSessionFactory,
} from "../../application/port/out/browser-session-factory";
import { PortalLoginServiceImpl } from "../../application/services/portal-login-service-impl";
import { LoginAttemptRegistry } from "../../application/services/login-attempt-registry";
import { LoginWatcher } from "../../application/services/login-watcher";
import { PortalSessionServiceImpl } from "../../application/services/portal-session-service-impl";
import { JOB_SEARCH_SERVICE } from "../../application/port/in/job-search-service";
import { JOB_SEARCH_PORTAL_REGISTRY, type JobSearchPortalRegistry } from "../../application/port/out/job-search-portal";
import { PORTAL_REGISTRY, type PortalRegistry } from "../../application/port/out/portal-registry";
import { DEFAULT_LIMITS, JobSearchFacade } from "../../application/services/job-search-facade";
import { systemClock } from "../time/system-clock";
import { BrowserModule } from "./browser.module";
import { PortalsModule } from "./portals.module";
import { scraperProperties } from "./scraper-properties";

/** Arma los servicios de aplicación (clases sin Nest) con sus dependencias y los expone por sus puertos de entrada. */
@Module({
  imports: [BrowserModule, PortalsModule],
  providers: [
    {
      provide: PORTAL_LOGIN_SERVICE,
      useFactory: (sessions: BrowserSessionFactory) =>
        new PortalLoginServiceImpl(
          sessions,
          new LoginAttemptRegistry(),
          new LoginWatcher(systemClock),
          new Logger("InteractiveLogin"),
        ),
      inject: [BROWSER_SESSION_FACTORY],
    },
    {
      provide: PORTAL_SESSION_SERVICE,
      useFactory: (sessions: BrowserSessionFactory) => new PortalSessionServiceImpl(sessions),
      inject: [BROWSER_SESSION_FACTORY],
    },
    {
      provide: JOB_SEARCH_SERVICE,
      useFactory: (searchPortals: JobSearchPortalRegistry, portals: PortalRegistry, sessions: BrowserSessionFactory) =>
        new JobSearchFacade({
          searchPortals,
          portals,
          sessions,
          clock: systemClock,
          logger: new Logger("JobSearch"),
          limits: { ...DEFAULT_LIMITS, maxPages: scraperProperties.maxSearchPages, showBrowser: scraperProperties.showSearchBrowser },
        }),
      inject: [JOB_SEARCH_PORTAL_REGISTRY, PORTAL_REGISTRY, BROWSER_SESSION_FACTORY],
    },
  ],
  exports: [PORTAL_LOGIN_SERVICE, PORTAL_SESSION_SERVICE, JOB_SEARCH_SERVICE],
})
export class ServicesModule {}
