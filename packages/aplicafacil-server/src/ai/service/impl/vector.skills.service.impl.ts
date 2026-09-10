import { SkillsVector } from "src/ai/models/skills.vector";
import { SkillsVectorService } from "../contract/vector.skills.service";
import { InjectRepository } from "@nestjs/typeorm";
import { Repository } from "typeorm";

export class SkillsVectorServiceImpl     extends SkillsVectorService {

    constructor(
        @InjectRepository(SkillsVector)
        private readonly skillsVectorRepository: Repository<SkillsVector>
    ) {
        super();
    }

    async getSkillsVectorByProfileId(profileId: string): Promise<SkillsVector> {
        const skillsVector: SkillsVector | null = await this.skillsVectorRepository.findOneBy(
            { 
                id: profileId
            }
        );
        if (!skillsVector) {
            // Handle the case where the vector is not found, e.g., throw an error or return a default value
            throw new Error(`Skills vector not found for profile ID: ${profileId}`);
        }
        return skillsVector;
    }

}