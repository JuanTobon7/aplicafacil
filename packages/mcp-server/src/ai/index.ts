import { Injectable } from "@nestjs/common";
import { ConfigService } from "@nestjs/config";
import { AiProvider } from "./adapter/contract/ai.provider.js";
import { OpenRouterAdapter } from "./adapter/impl/open.ai.adapter.js";

@Injectable()
export class AiProviderFactory {

    constructor(
        private readonly configService: ConfigService,
        private readonly openRouterAdapter: OpenRouterAdapter,
    ) {}

    getProvider(): AiProvider {

        const providerName = this.configService.get<string>(
            "LLM_PROVIDER",
            "openrouter",
        );

        switch (providerName.toLowerCase()) {
            case "openrouter":
                return this.openRouterAdapter;

            default:
                throw new Error(
                    `AiProvider "${providerName}" no soportado`,
                );
        }
    }
}