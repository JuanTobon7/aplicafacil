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
import { EasyApplyButtonClicker } from './easy-apply/contract/easy.apply.button.clicker';
import { EasyApplyButtonClickerImpl } from './easy-apply/impl/easy.apply.button.clicker.impl';
import { EasyApplyFormFiller } from './easy-apply/contract/easy.apply.form.filler';
import { EasyApplyFormFillerImpl } from './easy-apply/impl/easy.apply.form.filler.impl';
import { CaptchaDetector } from './captcha/contract/captcha.detector';
import { CaptchaDetectorImpl } from './captcha/impl/captcha.detector.impl';
import { SessionStore } from './session-store/contract/session.store';
import { SessionStoreImpl } from './session-store/impl/session.store.impl';
import { HumanBehaviorService } from '../common/human-behavior.service';

@Module({
  providers: [
    HumanBehaviorService,
    {
      provide: CaptchaDetector,
      useClass: CaptchaDetectorImpl,
    },
    {
      provide: SessionStore,
      useClass: SessionStoreImpl,
    },
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
    {
      provide: EasyApplyButtonClicker,
      useClass: EasyApplyButtonClickerImpl,
    },
    {
      provide: EasyApplyFormFiller,
      useClass: EasyApplyFormFillerImpl,
    },
  ],
  exports: [
    BrowserManager,
    LinkedInLoginComponent,
    JobSearchComponent,
    JobDetailExtractorComponent,
    EasyApplyComponent,
    CaptchaDetector,
    SessionStore,
    HumanBehaviorService,
  ],
})
export class LinkedInComponentsModule {}