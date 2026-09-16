import { IsOptional, IsString } from "class-validator";

export class CreatePeopleDto {
    @IsString()
    firstName!: string;
    @IsString()
    lastName!: string;
    @IsString()
    email!: string;
    @IsString()
    @IsOptional()
    phone?: string;
    @IsString()
    @IsOptional()
    linkedinUrl?: string;
}