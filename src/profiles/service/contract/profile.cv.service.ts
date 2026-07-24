import { ProfileResponseDto } from "src/profiles/dto/profile.response.dto";

export abstract class ProfileCvService {
    /**
     * 
     * @param id 
     * @param file 
     * 
     * @return {Promise<void>}
     */
    abstract uploadCv(id: string, file: any): Promise<void>;
    /**
     * 
     * @param id 
     * @return {Promise<any>} if found return the file, otherwise return
     */
    abstract getCv(id: string): Promise<any|null>;
    /**
     * 
     * @param id 
     * @return {Promise<void>}
     */
    abstract deleteCv(id: string): Promise<void>;

    abstract extractProfileDataFromCv(file: any): Promise<ProfileResponseDto>;
}