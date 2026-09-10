import { PeopleDto } from "../dto/people.dto.js";

export interface AiPeopleTool {
    getPeopleById(id: string): Promise<PeopleDto>;
}