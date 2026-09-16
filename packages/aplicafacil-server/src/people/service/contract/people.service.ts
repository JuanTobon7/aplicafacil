import { CreatePeopleDto } from "src/people/dto/people.create.dto";
import { PeopleDto } from "src/people/dto/people.dto";

export abstract class PeopleService {

    /**
     * Creates a new person.
     *
     * @param dto Data required to create a {@link PeopleDto}.
     * @returns A {@link PeopleDto} representing the created person.
     */
    abstract create(
        dto: CreatePeopleDto,
    ): Promise<PeopleDto>;

    /**
     * Retrieves all registered people.
     *
     * @returns A list of {@link PeopleDto}.
     */
    abstract findAll(): Promise<PeopleDto[]>;

    /**
     * Retrieves a person by its identifier.
     *
     * @param id Person identifier.
     * @returns The requested {@link PeopleDto}.
     */
    abstract findOne(
        id: string,
    ): Promise<PeopleDto>;

    /**
     * Updates an existing person.
     *
     * @param id Person identifier.
     * @param dto New data represented by {@link CreatePeopleDto}.
     * @returns The updated {@link PeopleDto}.
     */
    abstract update(
        id: string,
        dto: CreatePeopleDto,
    ): Promise<PeopleDto>;

    /**
     * Deletes a person.
     *
     * @param id Person identifier.
     */
    abstract remove(
        id: string,
    ): Promise<void>;
}