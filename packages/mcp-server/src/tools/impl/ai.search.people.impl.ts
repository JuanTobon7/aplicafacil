import { ClientPeople } from "../../client/client.people.js";
import { AiPeopleTool } from "../contract/ai.search.people.js";
import { PeopleDto } from "../dto/people.dto.js";

export class SearchPeopleToolImpl implements AiPeopleTool {
    constructor(
        private readonly clientPeople: ClientPeople  // Replace 'any' with the actual type of your clientPeople
    ) {
        this.clientPeople = clientPeople;
    }

    async getPeopleById(id: string): Promise<PeopleDto> {
        const people: PeopleDto = await this.clientPeople.getPeopleById(id);
        return people;
    }
}