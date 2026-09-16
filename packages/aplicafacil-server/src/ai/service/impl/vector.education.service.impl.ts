import { InjectRepository } from "@nestjs/typeorm";
import { EducationVectorService } from "../contract/vector.education.service";
import { EducationVector } from "src/ai/models/education.vector";
import { Repository } from "typeorm";

export class EducationServiceVectorImpl implements EducationVectorService {

    constructor(
        @InjectRepository(EducationVector)
        private readonly educationVectorRepository: Repository<EducationVector>,
    ) {}

    async getEducationVectorByProfileId(profileId: string): Promise<EducationVector> {
        const educationVector = await this.educationVectorRepository.findOneBy({
            id: profileId
        });

        if (!educationVector) {
            throw new Error(`Education vector not found for profile ID: ${profileId}`);
        }

        return educationVector;
    }

}