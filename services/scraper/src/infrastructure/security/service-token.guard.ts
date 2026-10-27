import { CanActivate, ExecutionContext, Injectable, UnauthorizedException } from "@nestjs/common";
import { Reflector } from "@nestjs/core";
import type { Request } from "express";
import { createRemoteJWKSet, jwtVerify } from "jose";
import { scraperProperties } from "../config/scraper-properties";
import { IS_PUBLIC } from "./public.decorator";

const jwks = createRemoteJWKSet(new URL(scraperProperties.jwksUrl));

/**
 * Solo linkedin-service puede usar el navegador: JWT de identity-service con roles=[SERVICE],
 * aud=aplicafacil-api y sub en la lista de clientes permitidos.
 */
@Injectable()
export class ServiceTokenGuard implements CanActivate {
  constructor(private readonly reflector: Reflector) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    if (this.reflector.getAllAndOverride<boolean>(IS_PUBLIC, [context.getHandler(), context.getClass()])) {
      return true;
    }
    const request = context.switchToHttp().getRequest<Request>();
    const header = request.headers.authorization ?? "";
    if (!header.startsWith("Bearer ")) throw new UnauthorizedException();
    try {
      const { payload } = await jwtVerify(header.slice(7), jwks, {
        issuer: scraperProperties.issuer,
        audience: scraperProperties.audience,
        algorithms: ["RS256"],
      });
      const roles = Array.isArray(payload.roles) ? payload.roles : [];
      return roles.includes("SERVICE") && scraperProperties.allowedClients.includes(String(payload.sub));
    } catch {
      throw new UnauthorizedException();
    }
  }
}
