import { PeopleDto } from "../tools/dto/people.dto.js";
import { ClientBackend } from "./client.backend.js";

export class ClientPeople {
    constructor(
        private readonly client: ClientBackend // Replace 'any' with the actual type of your client
    ) {
        this.client = client;
    }

    async getPeopleById(id: string): Promise<PeopleDto> { // Replace 'any' with the actual return type
        const people: PeopleDto = await this.client.get(`/people/${id}`);
        return people;
    }
}