import { ClientPeople } from "../../client/client.people.js";
import { ClientProfile } from "../../client/client.profile.js";
import { AiProfileTool } from "../contract/ai.search.profile.js";
import { ProfileVectorDto } from "@aplicafacil/core/domain";

export class AiSearchProfileToolImpl implements AiProfileTool {

    constructor(
        private readonly clientProfile: ClientProfile,
        private readonly clientPeople: ClientPeople
    ) {
        this.clientProfile = clientProfile;
        this.clientPeople = clientPeople;
    }

    async getProfileById(id: string): Promise<ProfileVectorDto> {

        const profile: ProfileVectorDto = await this.clientProfile.getProfile(id);

        if (!profile) {
            throw new Error("Profile not found");
        }

        return profile;
    }

    async getProfilesByUserId(userId: string): Promise<ProfileVectorDto[]> {

        return await this.clientProfile.getProfilesByUserId(userId);
    }
}