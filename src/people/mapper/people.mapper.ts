import { PeopleDto } from "../dto/people.dto";
import { PeopleModel } from "../models/people.model";

export class PeopleMapper {

    static toDto(entity: PeopleModel): PeopleDto {

        const dto = new PeopleDto();

        dto.id = entity.id;
        dto.firstName = entity.firstName;
        dto.lastName = entity.lastName;
        dto.email = entity.email;
        dto.phone = entity.phone;
        dto.linkedinUrl = entity.linkedinUrl;

        return dto;
    }

    static fromDto(dto: PeopleDto): PeopleModel {

        const entity = new PeopleModel();

        entity.id = dto.id;
        entity.firstName = dto.firstName;
        entity.lastName = dto.lastName;
        entity.email = dto.email;
        entity.phone = dto.phone;
        entity.linkedinUrl = dto.linkedinUrl;

        return entity;
    }

    static toDtoList(entities: PeopleModel[]): PeopleDto[] {
        return entities.map(this.toDto);
    }
}