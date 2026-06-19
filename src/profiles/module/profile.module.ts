import { ConfigModule } from "@nestjs/config";
import { Module } from '@nestjs/common';
import { TypeOrmModule } from "@nestjs/typeorm";
import { ProfileModel } from "../models/profiles.model";
import { ProfileController } from "../controller/profile.controller";
import { ProfileService } from "../service/contract/profile.service";
import { ProfileServiceImpl } from "../service/impl/profile.impl.service";
import { SkillsModel } from "../models/skills.model";
import { ExperiencesModel } from "../models/experiences.model";
import { EducationModel } from "../models/education.model";
import { ProjectModel } from "../models/projects.model";
import { PeopleModule } from "src/people/module/people.module";


@Module({
  imports: [
    PeopleModule,
    ConfigModule.forRoot({
      isGlobal: true,
    }),
    TypeOrmModule.forFeature([
      ProfileModel,
      SkillsModel,
      ExperiencesModel,
      EducationModel,
      ProjectModel
    ]),
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
