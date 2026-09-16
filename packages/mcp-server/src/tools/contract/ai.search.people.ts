import { PeopleDto } from "@aplicafacil/core/domain";

export interface AiPeopleTool {
    getPeopleById(id: string): Promise<PeopleDto>;
}