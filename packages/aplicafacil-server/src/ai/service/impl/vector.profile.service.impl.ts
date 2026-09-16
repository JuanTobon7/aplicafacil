import { InjectRepository } from "@nestjs/typeorm";
import { ProfileVectorService } from "../contract/vector.profile.service";
import { ProfilesVector } from "src/ai/models/profiles.vector";
import { Repository } from "typeorm";

export class ProfileVectorServiceImpl extends ProfileVectorService {
    constructor(
        @InjectRepository(ProfilesVector)
        private readonly profilesVectorRepository: Repository<ProfilesVector>,
    ) {
        super();
    }

    async getProfileById(id: string): Promise<ProfilesVector> {
        const profile = await this.profilesVectorRepository.findOneBy({ id });
        if(!profile) {
            throw new Error(`Profile with id ${id} not found`);
        }
        return profile;
    }

    async getProfilesByUserId(userId: string): Promise<ProfilesVector[]> {
        const profiles = await this.profilesVectorRepository.find({
            where: { id: userId }
        });
        return profiles;
    }

    async getRecommendationFormByProfileId(form: string,profileId: string): Promise<string> {
        const profile = await this.getProfileById(profileId);
        return profile.userId;
    }
}