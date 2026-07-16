import { SkillsVector } from "src/ai/models/skills.vector";

export abstract class SkillsVectorService {
    abstract getSkillsVectorByProfileId(profileId: string): Promise<SkillsVector>;
}