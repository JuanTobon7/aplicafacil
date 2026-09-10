import { Module } from '@nestjs/common';
import { ScheduleModule } from '@nestjs/schedule';
import { ScrapingLinkldnServiceImpl } from '../service/impl/scraping/scraping.linkldn.service.impl';
import { LinkedInComponentsModule } from '../components/linkedin.components.module';
import { JobRecommendationModule } from '../../jobs/module/job.recommendation.module';
import { JobWorkerAutomation } from '../worker/job.automation.worker';
import { JobAutomationHelperService } from '../service/job.automation.helper.service';
import { LinkedinQueueModule } from '../queue/linkedin.queue.module';
import { PeopleModule } from 'src/people/module/people.module';

@Module({
  imports: [
    ScheduleModule.forRoot(),
    LinkedInComponentsModule,
    JobRecommendationModule,
    LinkedinQueueModule,
    PeopleModule,
  ],
  providers: [
    {
      provide: 'ScrapingLinkldnService',
      useClass: ScrapingLinkldnServiceImpl,
    },
    JobAutomationHelperService,
    JobWorkerAutomation,
  ],
  exports: ['ScrapingLinkldnService'],
})
export class LinkedinAutomationModule {}
