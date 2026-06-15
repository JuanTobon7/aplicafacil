import { Injectable } from "@nestjs/common";
import { ConfigService } from "@nestjs/config";

import { LlmProvider } from "./adapter/contract/LlmProvider";
import { OpenRouterAdapter } from "./adapter/impl/OpenAiAdapter";

@Injectable()
export class AiProviderFactory {

    constructor(
        private readonly configService: ConfigService,
    ) {}

    getProvider(): LlmProvider {

        const providerName = this.configService.get<string>(
            "LLM_PROVIDER",
            "openrouter",
        );
        console.log("Selected LLM provider:", providerName);

        switch (providerName.toLowerCase()) {

            case "openrouter":
                return new OpenRouterAdapter();

            default:
                throw new Error(
                    `LlmProvider "${providerName}" no soportado`,
                );
        }
    }
}