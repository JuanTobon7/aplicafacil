// middleware/auth.middleware.ts

import {
  Injectable,
  NestMiddleware,
  UnauthorizedException,
} from '@nestjs/common';
import { Request, Response, NextFunction } from 'express';
import { JwtComponent } from '../contract/jwt.component';
import { JwtPayload } from 'src/auth/types/jwt.payload';

export interface AuthenticatedRequest extends Request {
  user: JwtPayload;
}

@Injectable()
export class AuthMiddleware implements NestMiddleware {
  constructor(private readonly jwtComponent: JwtComponent) {}

  async use(
    req: AuthenticatedRequest,
    res: Response,
    next: NextFunction,
  ): Promise<void> {
    const { token, fromCookie } = this.extractToken(req);

    if (!token) {
      throw new UnauthorizedException('Token no proporcionado');
    }

    req.user = await this.verifyToken(token);

    if (!fromCookie) {
      this.setTokenCookie(res, token);
    }

    next();
  }

  async verifyToken(token: string): Promise<JwtPayload> {
    try {
      return await this.jwtComponent.verify(token);
    } catch {
      throw new UnauthorizedException('Token inválido o expirado');
    }
  }

  private extractToken(
    req: Request,
  ): { token: string | undefined; fromCookie: boolean } {
    const authHeader = req.headers.authorization;
    const tokenCookie = req.cookies?.['access_token'];

    if (authHeader?.startsWith('Bearer ')) {
      const [, token] = authHeader.split(' ');
      return { token, fromCookie: false };
    }

    if (tokenCookie) {
      return { token: tokenCookie, fromCookie: true };
    }

    return { token: undefined, fromCookie: false };
  }

  private setTokenCookie(res: Response, token: string): void {
    res.cookie('access_token', token, {
      httpOnly: true,
      secure: process.env.NODE_ENV === 'production',
      sameSite: 'lax',
      maxAge: 24 * 60 * 60 * 1000, // 1 día, igual que expiresIn del JwtModule
    });
  }
}