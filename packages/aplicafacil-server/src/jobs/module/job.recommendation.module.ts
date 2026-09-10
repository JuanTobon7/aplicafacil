import { ConfigModule } from '@nestjs/config';
import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { JobRecommendationController } from '../controller/job.recommendation.controller';
import { McpClientModule } from '../../mcp-client/mcp-client.module';
import { RedisModule } from '../../common/redis/redis.module';

import { JobRecommendationService } from '../service/contract/job.recommendation.service';
import { JobRecommendationServiceImpl } from '../service/impl/job.recommendation.service';
import { JobsServiceImpl } from '../service/impl/jobs.service.impl';
import { ValidateJobsServiceImpl } from '../service/impl/validate-jobs.service.impl';
import { JobModel } from '../models/job.model';

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
    }),
    McpClientModule,
    RedisModule,
    TypeOrmModule.forFeature([JobModel]),
  ],
  controllers: [JobRecommendationController],
  providers: [
    {
      provide: JobRecommendationService,
      useClass: JobRecommendationServiceImpl,
    },
    {
      provide: 'JobsService',
      useClass: JobsServiceImpl,
    },
    {
      provide: 'ValidateJobsService',
      useClass: ValidateJobsServiceImpl,
    },
  ],
  exports: ['JobsService', 'ValidateJobsService'],
})
export class JobRecommendationModule {}
