import { z } from "zod";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { toolsBank } from "./src/tools/index.js";
import { AiProfileTool } from "./src/tools/contract/ai.search.profile.js";

const server = new McpServer({
  name: "greeting-server",
  version: "1.0.0"
});

server.registerTool(
  "search-profile-by-id",
  {
    title: "Search Profile By Id",
    inputSchema: {
      id: z.string().describe("The profile ID to search for")
    }
  },
  async ({ id }) => {
    const aiProfileTool =
      toolsBank.get<AiProfileTool>("aiProfileTool");

    const result =
      await aiProfileTool.getProfileById(id);

    return {
      content: [
        {
          type: "text",
          text: typeof result === "string" ? result : JSON.stringify(result)
        }
      ]
    };
  }
);