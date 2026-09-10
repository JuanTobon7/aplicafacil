import { JwtPayload } from "src/auth/types/jwt.payload";

export abstract class JwtComponent {
  abstract sign(payload: JwtPayload): Promise<string>;

  abstract verify<T = any>(token: string): Promise<JwtPayload>;
}