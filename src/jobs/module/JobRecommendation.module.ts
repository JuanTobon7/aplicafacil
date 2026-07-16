import { ConfigModule } from "@nestjs/config";
import { Module } from '@nestjs/common';
import { JobRecommendationController } from "../controller/JobRecommendation.controller";
import { McpClientModule } from "../../mcp-client/mcp-client.module";

import { JobRecommendationService } from "../service/contract/JobRecommendaion.service";
import { JobRecommendationServiceImpl } from "../service/impl/JobRecommendation.service";

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
    }),
    McpClientModule,
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
