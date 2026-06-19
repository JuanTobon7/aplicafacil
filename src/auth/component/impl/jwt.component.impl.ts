// services/jwt.service.ts

import { Injectable } from '@nestjs/common';
import { JwtComponent } from '../contract/jwt.component';
import { JwtService } from '@nestjs/jwt';
import { JwtPayload } from 'src/auth/types/jwt.payload';

@Injectable()
export class JwtServiceImpl extends JwtComponent {
  constructor(
    private readonly jwtService: JwtService,
  ) {
    super();
  }

  async sign(
    payload: JwtPayload,
  ): Promise<string> {
    return this.jwtService.signAsync(payload);
  }

  async verify<T = any>(
    token: string,
  ): Promise<JwtPayload> {
    return this.jwtService.verifyAsync<JwtPayload>(token);
  }
}