import { Body, Controller, Delete, Get, Param, Post, Put } from "@nestjs/common";
import { PeopleService } from "../service/contract/people.service";
import { CreatePeopleDto } from "../dto/people.create.dto";
import { PeopleDto } from "../dto/people.dto";

@Controller('people')
export class PeopleController {
    constructor(private readonly peopleService: PeopleService){}

    @Post()
    async create(
        @Body() createPeopleDto: CreatePeopleDto
    ):Promise<PeopleDto> {
        return await this.peopleService.create(createPeopleDto);
    }

    @Get()
    async findAll():Promise<PeopleDto[]> {
        return this.peopleService.findAll();
    }

    @Get(':id')
    async findOne(@Param('id') id: string):Promise<PeopleDto> {
        return await this.peopleService.findOne(id);
    }

    @Put(':id')
    async update(
        @Param('id') id: string,
        @Body() updatePeopleDto: CreatePeopleDto
    ):Promise<PeopleDto> {
        return await this.peopleService.update(id, updatePeopleDto);
    }

    @Delete(':id')
    async remove(@Param('id') id: string):Promise<void> {
        return await this.peopleService.remove(id);
    }
}