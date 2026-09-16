import { ConfigModule } from "@nestjs/config";
import { Module } from '@nestjs/common';

import { PeopleController } from "../controller/people.controller";
import { PeopleService } from "../service/contract/people.service";
import { PeopleServiceImpl } from "../service/impl/people.impl.service";
import { TypeOrmModule } from "@nestjs/typeorm";
import { PeopleModel } from "../models/people.model";

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
    }),
    TypeOrmModule.forFeature([PeopleModel]),
  ],
  controllers: [PeopleController],
  providers: [
    {
      provide: PeopleService,
      useClass: PeopleServiceImpl,
    },
  ],

  exports: [
    PeopleService,
  ],
})
export class PeopleModule {}
