import { CreatePeopleDto } from "../dto/people.create.dto";
import { PeopleDto } from "../dto/people.dto";
import { PeopleModel } from "../models/people.model";

export class PeopleCreateMapper {

    static toDto(entity: PeopleModel): PeopleDto {
        return {
            id: entity.id,
            firstName: entity.firstName,
            lastName: entity.lastName,
            email: entity.email,
            phone: entity.phone,
            linkedinUrl: entity.linkedinUrl,
        };
    }

    static toDtoList(entities: PeopleModel[]): PeopleDto[] {
        return entities.map(entity => this.toDto(entity));
    }

    static fromCreateDto(dto: CreatePeopleDto): PeopleModel {

        const entity = new PeopleModel();

        entity.firstName = dto.firstName;
        entity.lastName = dto.lastName;
        entity.email = dto.email;
        entity.phone = dto.phone;
        entity.linkedinUrl = dto.linkedinUrl;

        return entity;
    }

    static updateEntity(
        entity: PeopleModel,
        dto: CreatePeopleDto,
    ): PeopleModel {

        entity.firstName = dto.firstName;
        entity.lastName = dto.lastName;
        entity.email = dto.email;
        entity.phone = dto.phone;
        entity.linkedinUrl = dto.linkedinUrl;

        return entity;
    }
}