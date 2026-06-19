import { CreateUserDto } from "src/auth/dto/create.user.dto";
import { LoginDto } from "src/auth/dto/login.user.dto";

export abstract class AuthService {
  abstract sendToken(
    username: string,
    password: string,
  ): Promise<void>;

  abstract login(
    loginDto: LoginDto,
  ): Promise<{ access_token: string }>;

  abstract register(
    createUserDto: CreateUserDto,
  ): Promise<void>;
}