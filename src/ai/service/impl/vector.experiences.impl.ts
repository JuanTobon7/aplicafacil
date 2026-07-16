import { InjectRepository } from "@nestjs/typeorm";
import { ExperiencesVectorService } from "../contract/vector.experiences.service";
import { ExperiencesVector } from "src/ai/models/experiences.vector";
import { Repository } from "typeorm";

export class ExperiencesVectorServiceImpl extends ExperiencesVectorService {

    constructor(
        @InjectRepository(ExperiencesVector)
        private readonly experiencesVectorRepository: Repository<ExperiencesVector>
    ) {
        super();
    }
    async getExperiencesVectorByProfileId(profileId: string): Promise<ExperiencesVector> {
        const result = await this.experiencesVectorRepository.findOneBy({ id: profileId });
        if (!result) {
            // Handle the case where the vector is not found, e.g., throw an error or return a default value
            throw new Error(`Experiences vector not found for profile ID: ${profileId}`);
        }
        return result;
    }
}