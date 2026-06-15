import { CreatePeopleDto } from "src/people/dto/people.create.dto";
import { PeopleService } from "../contract/people.service";
import { PeopleDto } from "src/people/dto/people.dto";
import { InjectRepository } from "@nestjs/typeorm";
import { PeopleModel } from "src/people/models/people.model";
import { Repository } from "typeorm/browser/repository/Repository.js";
import { PeopleCreateMapper } from "src/people/mapper/people.create.mapper";
import { PeopleMapper } from "src/people/mapper/people.mapper";

export class PeopleServiceImpl extends PeopleService {

    constructor(
    @InjectRepository(PeopleModel)
        private readonly peopleRepository: Repository<PeopleModel>,
    ) {
        super();
    }

    async create(dto: CreatePeopleDto): Promise<PeopleDto> {
        const newPerson: PeopleDto = PeopleCreateMapper.toDto(this.peopleRepository.create(dto));
        const personEntity: PeopleModel = PeopleMapper.fromDto(newPerson);
        await this.peopleRepository.save(personEntity);
        return newPerson;
    }

    async findAll(): Promise<PeopleDto[]> {
        const peopleEntities: PeopleModel[] = await this.peopleRepository.find();
        return PeopleMapper.toDtoList(peopleEntities);
    }

    async findOne(id: string): Promise<PeopleDto> {
        const personEntity: PeopleModel | null = await this.peopleRepository.findOneBy({ id });
        if (!personEntity) {
            throw new Error(`Person with id ${id} not found`);
        }
        return PeopleMapper.toDto(personEntity);
    }

    async update(id: string, dto: CreatePeopleDto): Promise<PeopleDto> {
        const personEntity: PeopleModel | null = await this.peopleRepository.findOneBy({ id });
        if (!personEntity) {
            throw new Error(`Person with id ${id} not found`);
        }
        Object.assign(personEntity, dto);
        await this.peopleRepository.save(personEntity);
        return PeopleMapper.toDto(personEntity);
    }

    async remove(id: string): Promise<void> {
        const personEntity: PeopleModel | null = await this.peopleRepository.findOneBy({ id });
        if (!personEntity) {
            throw new Error(`Person with id ${id} not found`);
        }
        await this.peopleRepository.remove(personEntity);
    }
}