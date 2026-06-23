export interface AiProfileTool {
    getProfileById(id: string): Promise<string>;
    getProfilesByUserId(userId: string): Promise<string[]>;
    getRecommendationFormByProfileId(profileId: string): Promise<string>;
}