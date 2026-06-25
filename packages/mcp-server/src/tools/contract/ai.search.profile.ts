import { ProfileVectorDto } from "../dto/dto.vector.profile.js";

export interface AiProfileTool {
    getProfileById(id: string): Promise<ProfileVectorDto>;
    getProfilesByUserId(userId: string): Promise<ProfileVectorDto[]>;
    getRecommendationFormByProfileId(profileId: string): Promise<string>;
}