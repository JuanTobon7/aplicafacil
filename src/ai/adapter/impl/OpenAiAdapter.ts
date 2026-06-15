import { Injectable } from "@nestjs/common";
import OpenAI from "openai";

import { LlmProvider } from "../contract/LlmProvider";

@Injectable()
export class OpenRouterAdapter implements LlmProvider {

    private readonly client: OpenAI;

    constructor() {

        this.client = new OpenAI({
            apiKey: process.env.OPENROUTER_API_KEY,
            baseURL: process.env.OPENROUTER_BASE_URL,
        });
    }

    async fillForm(
        request: {
            system: string;
            prompt: string;
        }
    ): Promise<string> {

        const response =
            await this.client.chat.completions.create({
                model:
                    process.env.OPENROUTER_MODEL ??
                    "google/gemini-2.5-flash",

                temperature: 0,

                messages: [
                    {
                        role: "system",
                        content: request.system,
                    },
                    {
                        role: "user",
                        content: request.prompt,
                    },
                ],
            });

        return (
            response.choices[0]
                ?.message
                ?.content ?? ""
        );
    }
}