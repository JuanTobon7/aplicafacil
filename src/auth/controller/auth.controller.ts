import { Body, Controller, Post } from "@nestjs/common";
import { AuthService } from "../service/contract/auth.service";
import { LoginDto } from "../dto/login.user.dto";
import { CreateUserDto } from "../dto/create.user.dto";

@Controller('auth')
export class AuthController {
  constructor(
    private readonly authService: AuthService,
  ) {}

  @Post('login')
  async login(
    @Body() loginDto: LoginDto,
  ) {
    return this.authService.login(loginDto);
  }

  @Post('register')
  async register(
    @Body() createUserDto: CreateUserDto,
  ) {
    return this.authService.register(createUserDto);
  }
}