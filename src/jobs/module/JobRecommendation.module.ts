import { ConfigModule } from "@nestjs/config";
import { Module } from '@nestjs/common';
import { JobRecommendationController } from "../controller/JobRecommendation.controller";

import { AiProviderFactory } from "src/ai";
import { JobRecommendationService } from "../service/contract/JobRecommendaion.service";
import { JobRecommendationServiceImpl } from "../service/impl/JobRecommendation.service";

@Module({
  imports: [ConfigModule.forRoot({
      isGlobal: true,
    })],
  controllers: [JobRecommendationController],
  providers: [
    {
      provide: JobRecommendationService,
      useClass: JobRecommendationServiceImpl,
    },
    AiProviderFactory,
  ],
})
export class JobRecommendationModule {}
