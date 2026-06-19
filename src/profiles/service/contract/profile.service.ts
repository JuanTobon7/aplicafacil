import { CreateProfileDto } from "src/profiles/dto/create.profile.dto";
import { ProfileResponseDto } from "src/profiles/dto/profile.response.dto";

export abstract class ProfileService {
    abstract createProfile(profileData: CreateProfileDto, personId: string): Promise<ProfileResponseDto>;
    abstract getProfileById(id: string): Promise<ProfileResponseDto>;
    abstract getProfilesByPeopleId(id: string): Promise<ProfileResponseDto[]>;
    abstract updateProfile(id: string, profileData: CreateProfileDto): Promise<ProfileResponseDto>;
    abstract deleteProfile(id: string): Promise<void>;
}