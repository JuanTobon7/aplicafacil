import { Injectable } from "@nestjs/common";
import OpenAI from "openai";
import { AiProvider } from "../contract/ai.provider.js";
import { logger } from "../../../logger/logger.js";

@Injectable()
export class OmniRouteAdapter implements AiProvider {

    private readonly client: OpenAI;

    constructor() {
        const apiKey = process.env.OMNIROUTE_API_KEY;
        const baseURL = process.env.OMNIROUTE_BASE_URL ?? "http://localhost:20128/v1";

        if (!apiKey) {
            logger.warn("OmniRouteAdapter iniciado sin OMNIROUTE_API_KEY (puede que tu instancia no requiera auth)");
        }

        logger.debug("OmniRouteAdapter config", {
            baseURL,
            apiKeyPresent: !!apiKey,
            apiKeyPrefix: apiKey?.slice(0, 8),
        });

        this.client = new OpenAI({
            apiKey: apiKey ?? "not-needed-if-no-auth-configured",
            baseURL,
        });
    }

    async fillForm(
        request: {
            system: string;
            prompt: string;
        }
    ): Promise<string> {
        logger.info("OmniRouteAdapter.fillForm request", {
            system: request.system,
            prompt: request.prompt,
        });

        const response = await this.client.chat.completions.create({
            model: process.env.OMNIROUTE_MODEL ?? "auto",
            temperature: 0,
            messages: [
                { role: "system", content: request.system },
                { role: "user", content: request.prompt },
            ],
        });

        logger.info("OmniRouteAdapter.fillForm response", { response });

        return response.choices[0]?.message?.content ?? "";
    }

    async getEmbedding(data: any): Promise<number[]> {
        const response = await this.client.embeddings.create({
            model: process.env.OMNIROUTE_EMBEDDING_MODEL ?? "text-embedding-3-small",
            input: data,
        });
        return response.data[0].embedding;
    }

    async extractProfileFromCv(
        request: {
            system: string;
            prompt: string;
            data: string;
        }
    ): Promise<string> {
        logger.info("OmniRouteAdapter.extractProfileFromCv request", {
            system: request.system,
            prompt: request.prompt,
            data: request.data,
        });

        const response = await this.client.chat.completions.create({
            model: process.env.OMNIROUTE_MODEL ?? "auto",
            temperature: 0,
            messages: [
                { role: "system", content: request.system },
                { role: "user", content: request.prompt },
                { role: "user", content: request.data },
            ],
        });

        logger.info("OmniRouteAdapter.extractProfileFromCv response", { response });
        console.dir(response, { depth: null });

        return response.choices[0]?.message?.content ?? "";
    }
}