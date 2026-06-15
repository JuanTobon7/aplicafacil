export interface LlmProvider {

    fillForm(
        request: {
            system: string;
            prompt: string;
        }
    ): Promise<string>;
}