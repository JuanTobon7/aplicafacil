import { Profile } from "../../models/profile.js";
import { EmbeddingTool } from "../contract/embedding.js";

export class EmbeddingToolImpl implements EmbeddingTool {
    constructor(
        
    ) {}
    async getEmbedding(profile: Profile): Promise<number[]> {
        
        return Promise.resolve([]);
    }
}