import { ProfileResponseDto } from "src/profiles/dto/profile.response.dto";
import { ProfileService } from "../contract/profile.service";
import { CreateProfileDto } from "src/profiles/dto/create.profile.dto";
import { ProfileMapper } from "src/profiles/mapper/profile.mapper";
import { ProfileModel } from "src/profiles/models/profiles.model";
import { Repository } from "typeorm";
import { InjectRepository } from "@nestjs/typeorm";


export class ProfileServiceImpl extends ProfileService {

    constructor(
        @InjectRepository(ProfileModel)
            private readonly profileRepository: Repository<ProfileModel>,
        ) {
            super();
        }

    async createProfile(profileData: CreateProfileDto): Promise<ProfileResponseDto> {

        // Implement logic to create a profile
        const profile = ProfileMapper.toEntity(profileData);

        const saved = await this.profileRepository.save(profile);

        return ProfileMapper.toDto(saved);
    }
    
    async getProfileById(id: string): Promise<ProfileResponseDto> {
        // Implement logic to get a profile by ID
        const profile = await this.profileRepository.findOneBy({ id });
        if (!profile) {
            throw new Error(`Profile with id ${id} not found`);
        }
        return ProfileMapper.toDto(profile);
    }
    async updateProfile(id: string, profileData: CreateProfileDto): Promise<ProfileResponseDto> {
        // Implement logic to update a profile
        const profile = await this.profileRepository.findOneBy({ id });
        if (!profile) {
            throw new Error(`Profile with id ${id} not found`);
        }
        Object.assign(profile, profileData);
        const updatedProfile = await this.profileRepository.save(profile);
        return ProfileMapper.toDto(updatedProfile);
    }
    async deleteProfile(id: string): Promise<void> {
        // Implement logic to delete a profile
        const profile = await this.profileRepository.findOneBy({ id });
        if (!profile) {
            throw new Error(`Profile with id ${id} not found`);
        }
        await this.profileRepository.remove(profile);
    }
}