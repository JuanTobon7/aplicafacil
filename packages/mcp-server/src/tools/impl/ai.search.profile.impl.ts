import { ProfileRepository } from "../../repository/profile/contract/profile.repository.js";
import { AiProfileTool } from "../contract/ai.search.profile.js";

export class AiSearchProfileToolImpl implements AiProfileTool {
    constructor(
        private readonly profileRepository: ProfileRepository
    ) {}

    async getProfileById(id: string): Promise<string> {
        const profile = await this.profileRepository.getById(id);

        if (!profile) {
            return "Profile not found";
        }

        return JSON.stringify(profile, null, 2);
    }

    async getProfilesByUserId(userId: string): Promise<string[]> {
        const profiles = await this.profileRepository.getByUserId(userId);

        return profiles.map(profile => JSON.stringify(profile));
    }

    async getRecommendationFormByProfileId(profileId: string): Promise<string> {
        const profile = await this.profileRepository.getById(profileId);

        if (!profile) {
            return "Profile not found";
        }

        return `
        Candidate: ${profile.fullName}
        Profession: ${profile.profession}

        Recommendation:

        Strengths:
        - 

        Areas for improvement:
        -

        General comments:
        -
                `;
        }
}