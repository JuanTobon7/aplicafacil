export interface AiProvider {

    fillForm(
        request: {
            system: string;
            prompt: string;
        }
    ): Promise<string>;

    getEmbedding(data: any): Promise<number[]>;
}