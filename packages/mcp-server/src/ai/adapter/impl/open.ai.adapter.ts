import { Injectable } from "@nestjs/common";
import OpenAI from "openai";
import { AiProvider } from "../contract/ai.provider.js";
import { logger } from "../../../logger/logger.js";
import { httpInterceptor } from "../../../http/http-interceptor.js";

@Injectable()
export class OpenRouterAdapter implements AiProvider {

    private readonly client: OpenAI;

    constructor() {

        this.client = new OpenAI({
            apiKey: process.env.OPENROUTER_API_KEY!,
            baseURL: process.env.OPENROUTER_BASE_URL!,
            // Wrap fetch to log all HTTP traffic to OpenRouter
            fetch: httpInterceptor.createFetchWrapper("OpenRouter") as any,
        });
    }

    async fillForm(
        request: {
            system: string;
            prompt: string;
        }
    ): Promise<string> {
        logger.info("OpenRouterAdapter.fillForm request", {
            system: request.system,
            prompt: request.prompt,
        });
        logger.debug("env.OPENROUTER_API_KEY", {
            apiKey: process.env.OPENROUTER_API_KEY ?? "not set",
        });
        logger.debug("env.OPENROUTER_BASE_URL", {
            baseURL: process.env.OPENROUTER_BASE_URL ?? "not set",
        });
        logger.debug("env.OPENROUTER_MODEL", {
            model: process.env.OPENROUTER_MODEL ?? "not set",
        });
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

        logger.info("OpenRouterAdapter.fillForm response", {
            response: response
        });
        return (
            response.choices[0]
                ?.message
                ?.content ?? ""
        );
    }

    async getEmbedding(data: any): Promise<number[]> {
        const response = await this.client.embeddings.create({
            model: process.env.OPENROUTER_EMBEDDING_MODEL ?? "text-embedding-3-small",
            input: data,
        });
        return response.data[0].embedding;
    }

    async extractProfileFromCv(
        request: {
            system: string;
            prompt: string;
            data: string
        }
    ): Promise<string> {
        logger.info("OpenRouterAdapter.extractProfileFromCv request", {
            system: request.system,
            prompt: request.prompt,
            data: request.data,
        });
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
                    {
                        content: request.data,
                        role: "user",
                    }
                ],
            });
        logger.info("OpenRouterAdapter.extractProfileFromCv response", {
            response: response
        });
        console.dir(response, { depth: null });
        return (
            response.choices[0]
                ?.message
                ?.content ?? ""
        );
    }
}