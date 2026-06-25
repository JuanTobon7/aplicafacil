export interface EmbeddingTool {
    getEmbedding(profile: ProfileVectorDto): Promise<ProfileVectorDto[]>
}