import { ProfileVectorDto } from "@aplicafacil/core/domain";

export interface AiProfileTool {
    getProfileById(id: string): Promise<ProfileVectorDto>;
    getProfilesByUserId(userId: string): Promise<ProfileVectorDto[]>;
}