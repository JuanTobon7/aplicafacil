import { Module } from '@nestjs/common';
import { BrowserManager } from './browser-manager/contract/browser.manager';
import { BrowserManagerImpl } from './browser-manager/impl/browser.manager.impl';
import { LinkedInLoginComponent } from './login/contract/linkedin.login.component';
import { LinkedInLoginComponentImpl } from './login/impl/linkedin.login.component.impl';
import { JobSearchComponent } from './job-search/contract/job.search.component';
import { JobSearchComponentImpl } from './job-search/impl/job.search.component.impl';
import { JobDetailExtractorComponent } from './job-detail/contract/job.detail.extractor.component';
import { JobDetailExtractorComponentImpl } from './job-detail/impl/job.detail.extractor.component.impl';
import { EasyApplyComponent } from './easy-apply/contract/easy.apply.component';
import { EasyApplyComponentImpl } from './easy-apply/impl/easy.apply.component.impl';

@Module({
  providers: [
    {
      provide: BrowserManager,
      useClass: BrowserManagerImpl,
    },
    {
      provide: LinkedInLoginComponent,
      useClass: LinkedInLoginComponentImpl,
    },
    {
      provide: JobSearchComponent,
      useClass: JobSearchComponentImpl,
    },
    {
      provide: JobDetailExtractorComponent,
      useClass: JobDetailExtractorComponentImpl,
    },
    {
      provide: EasyApplyComponent,
      useClass: EasyApplyComponentImpl,
    },
  ],
  exports: [
    BrowserManager,
    LinkedInLoginComponent,
    JobSearchComponent,
    JobDetailExtractorComponent,
    EasyApplyComponent,
  ],
})
export class LinkedInComponentsModule {}