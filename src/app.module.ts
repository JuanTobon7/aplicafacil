import { ConfigModule, ConfigService } from "@nestjs/config";
import { Module } from '@nestjs/common';
import { AppController } from './app.controller';
import { AppService } from './app.service';
import { JobRecommendationModule } from "./jobs/module/JobRecommendation.module";
import { ThrottlerModule } from "@nestjs/throttler";
import { PeopleModule } from "./people/module/people.module";
import { TypeOrmModule } from "@nestjs/typeorm";
import { databaseConfig } from "./config/db.config";
import { ProfileModule } from "./profiles/module/profile.module";
import { AuthModule } from "./auth/module/user.module";

@Module({
  imports: [
    JobRecommendationModule,
    PeopleModule,
    ProfileModule,
    AuthModule,
    ConfigModule.forRoot({
      isGlobal: true,
    }),
    ThrottlerModule.forRoot([
      {
        ttl: 60_000, // 1 minuto
        limit: 30,   // 30 peticiones por IP
      },
    ]),
    TypeOrmModule.forRootAsync({
        inject: [ConfigService],
        useFactory: databaseConfig,
    }),
  ],
  controllers: [AppController],
  providers: [AppService],
})
export class AppModule {}
