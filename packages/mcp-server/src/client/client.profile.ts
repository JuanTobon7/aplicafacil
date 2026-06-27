import { ProfileVectorDto } from "../tools/dto/dto.vector.profile.js";
import { ClientBackend } from "./client.backend.js";

export class ClientProfile {
    constructor(
        private readonly backend: ClientBackend
    ) {
        this.backend = backend;
    
    }

    async getProfile(profileId: string): Promise<ProfileVectorDto> {
        const profile : ProfileVectorDto = await this.backend.get(`/profile/${profileId}`);
        return profile;
    }

    async getProfilesByUserId(userId: string): Promise<ProfileVectorDto[]> {
        const profiles : ProfileVectorDto[] = await this.backend.get(`/profiles/user/${userId}`);
        return profiles;
    }

}