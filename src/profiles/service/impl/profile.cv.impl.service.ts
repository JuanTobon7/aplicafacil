import { File } from "buffer";
import { ProfileCvService } from "../contract/profile.cv.service";
import { FileInterceptorFactory } from "src/components/storage.media/impl/file.factory.interceptor";

export class ProfileCvServiceImpl implements ProfileCvService {

    async uploadCv(id: string, file: File): Promise<void> {
        // Implement logic to upload CV file for the profile with the given ID
        const interceptor = FileInterceptorFactory.fromMimeType(file.type);
        const sanitizedFile = await interceptor.sanitizeFile(file);
        const reducedFile = await interceptor.reduceFile(sanitizedFile);
        const buffer = await interceptor.getBufferFromFile(reducedFile);
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