import { AiProfileTool } from "../contract/ai.search.profile.js";
import { ProfileVectorDto } from "../dto/dto.vector.profile.js";

export class AiSearchProfileToolImpl implements AiProfileTool {

    async getProfileById(id: string): Promise<ProfileVectorDto> {

        if (!profile) {
            throw new Error("Profile not found");
        }

        return JSON.stringify(profile, null, 2);
    }

    async getProfilesByUserId(userId: string): Promise<ProfileVectorDto[]> {

        return profiles.map(profile => JSON.stringify(profile));
    }

    async getRecommendationFormByProfileId(profileId: string): Promise<string> {

        if (!profile) {
            throw new Error("Profile not found");
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