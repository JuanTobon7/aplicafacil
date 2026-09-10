import { Body, Controller, Delete, Get, Inject, Param, Post, Put, Req, UploadedFile, UseInterceptors } from "@nestjs/common";
import { ProfileService } from "../service/contract/profile.service";
import { ProfileResponseDto } from "../dto/profile.response.dto";
import { CreateProfileDto } from "../dto/create.profile.dto";
import { FileInterceptor } from "@nestjs/platform-express";
import { JwtPayload } from "src/auth/types/jwt.payload";
import { ProfileCvService } from "../service/contract/profile.cv.service";

@Controller("profiles")
export class ProfileController {
    constructor(
        @Inject(ProfileService)
        private readonly profileService: ProfileService,
        @Inject(ProfileCvService)
        private readonly profileCvService: ProfileCvService
    ) {}

    @Post()
    async createProfile(
        @Req() req: any,
        @Body() profileData: CreateProfileDto): Promise<ProfileResponseDto> {
        // Implement logic to create a profile
        const reqUser = req.user;
        console.debug("Creating profile for user:", reqUser);  
        return await this.profileService.createProfile(profileData, reqUser.personId);
    }

    @Get()
    async getProfileByUserId(@Req() req: any):Promise<ProfileResponseDto[]> {
        const reqUser :JwtPayload = req.user;
        console.debug("Fetching profiles for user:", reqUser);
        return await this.profileService.getProfilesByPeopleId(reqUser.personId);
    }

    @Get(":id")
    async getProfileById(@Param("id") id: string): Promise<ProfileResponseDto> {
        return await this.profileService.getProfileById(id);
    }

    @Put(":id")
    async updateProfile(@Param("id") id: string, @Body() profileData: CreateProfileDto): Promise<ProfileResponseDto> {
        return await this.profileService.updateProfile(id, profileData);
    }

    @Delete(":id")
    async deleteProfile(@Param("id") id: string): Promise<void> {
        return await this.profileService.deleteProfile(id);
    }

    @Post(":id/cv")
    @UseInterceptors(FileInterceptor('file'))
    async uploadCV(@Param("id") id: string, 
    @UploadedFile() file: any): Promise<void> {
        return this.profileCvService.uploadCv(id, file);
    }

    @Post("cv/extract")
    @UseInterceptors(FileInterceptor('cv'))
    async extractProfileDataFromCv(@UploadedFile() file: Express.Multer.File): Promise<ProfileResponseDto> {
        return await this.profileCvService.extractProfileDataFromCv(file);
    }
}