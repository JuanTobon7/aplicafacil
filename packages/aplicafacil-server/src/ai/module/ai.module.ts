import { Module } from "@nestjs/common";
import { AiControllerTools } from "../controllers/ai.controller";
import { ConfigModule } from "@nestjs/config";
import { TypeOrmModule } from "@nestjs/typeorm";
import { EducationVector } from "../models/education.vector";
import { ExperiencesVector } from "../models/experiences.vector";
import { ProfilesVector } from "../models/profiles.vector";
import { SkillsVector } from "../models/skills.vector";
import { ProfileVectorService } from "../service/contract/vector.profile.service";
import { ProfileVectorServiceImpl } from "../service/impl/vector.profile.service.impl";

@Module({
    controllers: [
        AiControllerTools
    ],
    imports: [
        ConfigModule.forRoot({
            isGlobal: true,
        }),
        TypeOrmModule.forFeature([
            EducationVector,
            ExperiencesVector,
            SkillsVector,
            ProfilesVector
        ])
    ],
    providers: [
        {
            provide: ProfileVectorService,
            useClass: ProfileVectorServiceImpl
        }
    ],
    exports: [
        ProfileVectorService
    ]
})

export class AiModule {}