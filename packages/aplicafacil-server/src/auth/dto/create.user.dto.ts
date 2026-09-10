import { IsString, IsStrongPassword,  } from "class-validator";

export class CreateUserDto {
  @IsString()
  username!: string;
  @IsString()
  @IsStrongPassword({
    minLength: 8,
    minLowercase: 1,
    minUppercase: 1,
    minNumbers: 1,
    minSymbols: 0,
  })
  password!: string;
  @IsString()
  personId!: string;
}