import { ConfigModule } from "@nestjs/config";
import { Module } from '@nestjs/common';
import { AppController } from './app.controller';
import { AppService } from './app.service';
import { JobRecommendationModule } from "./jobs/module/JobRecommendation.module";
import { ThrottlerModule } from "@nestjs/throttler";

@Module({
  imports: [
    JobRecommendationModule,
    ConfigModule.forRoot({
      isGlobal: true,
    }),
    ThrottlerModule.forRoot([
      {
        ttl: 60_000, // 1 minuto
        limit: 30,   // 30 peticiones por IP
      },
    ]),
  ],
  controllers: [AppController],
  providers: [AppService],
})
export class AppModule {}
