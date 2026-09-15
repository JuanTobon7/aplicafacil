import { Module } from '@nestjs/common';
import { ScheduleModule } from '@nestjs/schedule';
import { LinkedInComponentsModule } from '../components/linkedin.components.module';
import { JobRecommendationModule } from '../../jobs/module/job.recommendation.module';
import { JobWorkerAutomation } from '../worker/job.automation.worker';
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
  providers: [JobWorkerAutomation],
})
export class LinkedinAutomationModule {}
