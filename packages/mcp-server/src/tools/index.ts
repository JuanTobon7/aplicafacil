import { PostgresProfileRepositoryImpl } from "../repository/profile/impl/profile.repository.pg.js";
import { AiSearchProfileToolImpl } from "./impl/ai.search.profile.impl.js";

export class ToolsBank {
    private readonly tools = new Map<string, any>();

    register(name: string, tool: any): void {
        this.tools.set(name, tool);
    }

    get<T>(name: string): T {
        const tool = this.tools.get(name);

        if (!tool) {
            throw new Error(`Tool '${name}' not found`);
        }

        return tool as T;
    }

    has(name: string): boolean {
        return this.tools.has(name);
    }
}

export const toolsBank = new ToolsBank();

toolsBank.register(
    "aiProfileTool",
    new AiSearchProfileToolImpl(
        new PostgresProfileRepositoryImpl()
    )
);