import { Module } from '@nestjs/common';
import { ScheduleModule } from '@nestjs/schedule';
import { TypeOrmModule } from '@nestjs/typeorm';
import { ScrapingLinkldnServiceImpl } from '../service/impl/scraping/scraping.linkldn.service.impl';
import { LinkedInComponentsModule } from '../components/linkedin.components.module';
import { JobRecommendationModule } from '../../jobs/module/job.recommendation.module';
import { AutomationScheduler } from '../scheduler/automation.scheduler';
import { SearchJobsProcessor } from '../worker/search-jobs.processor';
import { ApplyJobProcessor } from '../worker/apply-job.processor';
import { AutomationService } from '../service/contract/automation.service';
import { AutomationServiceImpl } from '../service/impl/automation.service.impl';
import { AutomationConfigModel } from '../models/automation-config.model';
import { LinkedInAccountModel } from '../models/linkedin-account.model';
import { LinkedinQueueModule } from '../queue/linkedin.queue.module';
import { LinkedinAccountLockService } from '../service/linkedin.account.lock.service';
import { ProfileModule } from 'src/profiles/module/profile.module';

@Module({
  imports: [
    ScheduleModule.forRoot(),
    LinkedInComponentsModule,
    JobRecommendationModule,
    LinkedinQueueModule,
    ProfileModule,
    TypeOrmModule.forFeature([AutomationConfigModel, LinkedInAccountModel]),
  ],
  providers: [
    {
      provide: 'ScrapingLinkldnService',
      useClass: ScrapingLinkldnServiceImpl,
    },
    {
      provide: AutomationService,
      useClass: AutomationServiceImpl,
    },
    LinkedinAccountLockService,
    AutomationScheduler,
    SearchJobsProcessor,
    ApplyJobProcessor,
  ],
  exports: ['ScrapingLinkldnService', AutomationService],
})
export class LinkedinAutomationModule {}
