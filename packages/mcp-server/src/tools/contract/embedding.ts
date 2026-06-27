import { ProfileVectorDto } from "../dto/dto.vector.profile.js";

export interface EmbeddingTool {
    getEmbedding(profile: ProfileVectorDto): Promise<ProfileVectorDto[]>
}