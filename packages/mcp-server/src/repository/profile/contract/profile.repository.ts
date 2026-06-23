import { Profile } from "../../../models/profile.js";

export interface ProfileRepository {
    save(profile: Profile): Promise<void>;
    getById(id: string): Promise<Profile | null>;
    getByUserId(userId: string): Promise<Profile[]>;
}