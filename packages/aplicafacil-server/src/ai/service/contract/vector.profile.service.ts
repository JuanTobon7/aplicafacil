import { ProfilesVector } from "src/ai/models/profiles.vector";

export abstract class ProfileVectorService {
    abstract getProfileById(id: string): Promise<ProfilesVector>;
    abstract getProfilesByUserId(userId: string): Promise<ProfilesVector[]>;
    abstract getRecommendationFormByProfileId(form: string,profileId: string): Promise<string>;
}