import { ExperiencesVector } from "src/ai/models/experiences.vector";

export abstract class ExperiencesVectorService {
    abstract getExperiencesVectorByProfileId(profileId: string): Promise<ExperiencesVector>;
}