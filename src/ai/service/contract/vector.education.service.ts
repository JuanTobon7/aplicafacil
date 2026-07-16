import { EducationVector } from "src/ai/models/education.vector";

export abstract class EducationVectorService {
    abstract getEducationVectorByProfileId(profileId: string): Promise<EducationVector>;
}