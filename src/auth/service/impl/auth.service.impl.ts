import { Injectable, NotFoundException, UnauthorizedException } from "@nestjs/common";
import { AuthService } from "../contract/auth.service";
import { InjectRepository } from "@nestjs/typeorm";
import { UserModel } from "src/auth/models/user.model";
import { Repository } from "typeorm";
import { LoginDto } from "src/auth/dto/login.user.dto";
import { CreateUserDto } from "src/auth/dto/create.user.dto";
import { JwtService } from "@nestjs/jwt";
import * as bcrypt from 'bcrypt';
import { PeopleService } from "src/people/service/contract/people.service";

@Injectable()
export class AuthServiceImpl implements AuthService {
  constructor(
    @InjectRepository(UserModel)
    private readonly userRepository: Repository<UserModel>,
    private readonly jwtService: JwtService,
    private readonly peopleService: PeopleService,
  ) {}

  async sendToken(
    username: string,
    password: string,
  ): Promise<void> {
    const user = await this.userRepository.findOne({
      where: { username },
      relations: {
        person: true,
      }
    });

    if (!user) {
      throw new NotFoundException('Usuario no encontrado');
    }

    const validPassword = await bcrypt.compare(
      password,
      user.password,
    );

    if (!validPassword) {
      throw new UnauthorizedException('Credenciales inválidas');
    }

  }

  async login(
    loginDto: LoginDto,
  ): Promise<{ access_token: string }> {
    const user = await this.validateUser(
      loginDto.username,
      loginDto.password,
    );

    if (!user) {
      throw new UnauthorizedException(
        'Credenciales inválidas',
      );
    }

    const payload = {
      sub: user.id,
      username: user.username,
      personId: user.person.id,
    };

    return {
      access_token: await this.jwtService.signAsync(payload),
    };
  }

  async register(
    createUserDto: CreateUserDto,
  ): Promise<void> {
    const hash = await bcrypt.hash(
      createUserDto.password,
      10,
    );

    const person = await this.peopleService.findOne(createUserDto.personId);

    if(!person) throw new NotFoundException('Persona no encontrada');
    
    const user = this.userRepository.create({
      username: createUserDto.username,
      password: hash,
      person: person
    });

    await this.userRepository.save(user);
  }
 
  async validateUser(
    username: string,
    password: string,
  ): Promise<UserModel> {
    const user = await this.userRepository.findOne({
      where: {
        username,
      },
      relations: {
        person: true,
      },
    });

    if (!user) {
      throw new NotFoundException(
        'Usuario no encontrado',
      );
    }

    const validPassword = await bcrypt.compare(
      password,
      user.password,
    );

    if (!validPassword) {
      throw new UnauthorizedException(
        'Credenciales inválidas',
      );
    }

    return user;
  }
}