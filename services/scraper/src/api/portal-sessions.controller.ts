import { Body, Controller, Delete, HttpCode, Param, Post, Res } from "@nestjs/common";
import type { Response } from "express";
import { SessionsHttpHandler } from "./sessions-http-handler";

/** API interna por portal: /portals/linkedin/sessions/..., /portals/computrabajo/sessions/... */
@Controller("portals/:portal/sessions")
export class PortalSessionsController {
  constructor(private readonly handler: SessionsHttpHandler) {}

  @Post("login")
  login(@Param("portal") portal: string, @Body() body: unknown, @Res() res: Response) {
    return this.handler.login(portal, body, res);
  }

  @Delete("login/:attemptId")
  @HttpCode(202)
  cancel(@Param("attemptId") attemptId: string) {
    this.handler.cancel(attemptId);
  }

  @Post("validate")
  @HttpCode(200)
  validate(@Param("portal") portal: string, @Body() body: unknown) {
    return this.handler.validate(portal, body);
  }

  @Post("read-profile")
  @HttpCode(200)
  readProfile(@Param("portal") portal: string, @Body() body: unknown) {
    return this.handler.readProfile(portal, body);
  }
}
