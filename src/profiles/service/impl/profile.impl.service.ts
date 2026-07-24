import { ProfileResponseDto } from "src/profiles/dto/profile.response.dto";
import { ProfileService } from "../contract/profile.service";
import { CreateProfileDto } from "src/profiles/dto/create.profile.dto";
import { ProfileMapper } from "src/profiles/mapper/profile.mapper";
import { ProfileModel } from "src/profiles/models/profiles.model";
import { Repository } from "typeorm";
import { InjectRepository } from "@nestjs/typeorm";
import { PeopleService } from "src/people/service/contract/people.service";
import { PeopleMapper } from "src/people/mapper/people.mapper";


export class ProfileServiceImpl extends ProfileService {

    constructor(
            @InjectRepository(ProfileModel)
            private readonly profileRepository: Repository<ProfileModel>,

            private readonly peopleService: PeopleService
        ) {
            super();
        }

    
    async createProfile(profileData: CreateProfileDto, peopleId: string): Promise<ProfileResponseDto> {

        // Implement logic to create a profile
        const profile = ProfileMapper.toEntity(profileData);
        const people = await this.peopleService.findOne(peopleId);
        profile.people = PeopleMapper.fromDto(people);

        const saved = await this.profileRepository.save(profile);

        return ProfileMapper.toDto(saved);
    }

    async getProfilesByPeopleId(id: string): Promise<ProfileResponseDto[]> {
        console.debug(`Fetching profiles for person with id: ${id}`);
        const profiles = await this.profileRepository.findBy(
            { 
                people: {id: id} 
            }
        );
        console.debug(`Found ${profiles.length} profiles for person with id: ${id}`);
        return profiles.map(ProfileMapper.toDto);
    }

    async getProfilesById(id: string): Promise<ProfileResponseDto> {
        // Implement logic to get a profile by ID
        const profile = await this.profileRepository.findOneBy({ id });
        if (!profile) {
            throw new Error(`Profile with id ${id} not found`);
        }
        return ProfileMapper.toDto(profile);
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