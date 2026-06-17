import { ProfileCvService } from "../contract/profile.cv.service";

export class ProfileCvServiceImpl implements ProfileCvService {
    async uploadCv(id: string, file: any): Promise<void> {
        // Implement logic to upload CV file for the profile with the given ID
    }

    async getCv(id: string): Promise<any|null> {
        // Implement logic to retrieve the CV file for the profile with the given ID
        // Return the file if found, otherwise return null
        return null;
    }

    async deleteCv(id: string): Promise<void> {
        // Implement logic to delete the CV file for the profile with the given ID
    }
}