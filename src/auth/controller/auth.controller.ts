import { Body, Controller, Post, Res } from "@nestjs/common";
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
    @Res() Response: any,
  ):Promise<string> {
    const response = await this.authService.login(loginDto);
    Response.cookie('access_token', 
      response.access_token, 
      { 
        httpOnly: true,
        sameSite: 'lax',
        secure: process.env.NODE_ENV === 'production',
      }
    );
    return Response.status(202).send("Login successful");
  }

  @Post('register')
  async register(
    @Body() createUserDto: CreateUserDto,
  ) {
    return this.authService.register(createUserDto);
  }
}