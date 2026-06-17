import { ConfigModule } from "@nestjs/config";
import { Module } from '@nestjs/common';
import { TypeOrmModule } from "@nestjs/typeorm";
import { ProfileModel } from "../models/profiles.model";
import { ProfileController } from "../controller/profile.controller";
import { ProfileService } from "../service/contract/profile.service";
import { ProfileServiceImpl } from "../service/impl/profile.impl.service";


@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
    }),
    TypeOrmModule.forFeature([ProfileModel]),
  ],
  controllers: [ProfileController],
  providers: [
    {
      provide: ProfileService,
      useClass: ProfileServiceImpl,
    },
  ],
})
export class ProfileModule {}
