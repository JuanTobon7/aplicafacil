import { Axios } from "axios";
import { ClientBackend } from "../client/client.backend.js";
import { ClientPeople } from "../client/client.people.js";
import { ClientProfile } from "../client/client.profile.js";
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



const toolsBank = new ToolsBank();

const clientBackend = new ClientBackend(
    process.env.BACKEND_URL ?? "http://localhost:3000",
    new Axios({
        baseURL: process.env.BACKEND_URL ?? "http://localhost:3000",
        headers: {
            "Content-Type": "application/json",
        },
        timeout: 5000,
    })
);
const clientProfile = new ClientProfile(clientBackend);
const clientPeople = new ClientPeople(clientBackend);

const AiProfileTool = new AiSearchProfileToolImpl(
    clientProfile,
    clientPeople
);

toolsBank.register(
    "search job profile embedding by id",
    AiProfileTool.getProfileById.bind(AiProfileTool),
);

toolsBank.register(
    "search job profiles by user id",
    AiProfileTool.getProfilesByUserId.bind(AiProfileTool),
);

toolsBank.register(
    "get recommendation form by profile id",
    AiProfileTool.getRecommendationForm.bind(AiProfileTool),
);

export {toolsBank};

