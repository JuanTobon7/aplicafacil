import { ConfigModule } from "@nestjs/config";
import { Module } from '@nestjs/common';
import { JobRecommendationController } from "../controller/job.recommendation.controller";
import { McpClientModule } from "../../mcp-client/mcp-client.module";
import { RedisModule } from "../../common/redis/redis.module";

import { JobRecommendationService } from "../service/contract/job.recommendation.service";
import { JobRecommendationServiceImpl } from "../service/impl/job.recommendation.service";

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
    }),
    McpClientModule,
    RedisModule,
  ],
  controllers: [JobRecommendationController],
  providers: [
    {
      provide: JobRecommendationService,
      useClass: JobRecommendationServiceImpl,
    },
  ],
})
export class JobRecommendationModule {}
