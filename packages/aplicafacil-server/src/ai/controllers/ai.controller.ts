import { Body, Controller, Get } from "@nestjs/common";
import { ProfileVectorDto } from "../dto/dto.vector.profile";
import { ProfileVectorService } from "../service/contract/vector.profile.service";

@Controller("ai/tools")
export class AiControllerTools {

    constructor(
        private readonly profileVectorService: ProfileVectorService,
    ){}

    @Get("profile/:profileId")
    async getProfileById(@Body("profileId") profileId: string): Promise<ProfileVectorDto> {
        const result = await this.profileVectorService.getProfileById(profileId);
        return result;
    }

    @Get("profiles/user/:userId")
    async getProfilesByUserId(@Body("userId") userId: string): Promise<ProfileVectorDto[]> {
        const result = await this.profileVectorService.getProfilesByUserId(userId);
        return result;
    }

}