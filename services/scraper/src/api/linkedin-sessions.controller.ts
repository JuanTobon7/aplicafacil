import { Body, Controller, Delete, HttpCode, Param, Post, Res } from "@nestjs/common";
import type { Response } from "express";
import { LINKEDIN_PORTAL_ID } from "../infrastructure/portal/linkedin/linkedin.portal";
import { SessionsHttpHandler } from "./sessions-http-handler";

/**
 * Rutas /sessions/* de contracts/openapi/scraper-browser.yaml que ya usa linkedin-service. Equivalen a
 * /portals/linkedin/sessions/*.
 */
@Controller("sessions")
export class LinkedInSessionsController {
  constructor(private readonly handler: SessionsHttpHandler) {}

  @Post("login")
  login(@Body() body: unknown, @Res() res: Response) {
    return this.handler.login(LINKEDIN_PORTAL_ID, body, res);
  }

  @Delete("login/:attemptId")
  @HttpCode(202)
  cancel(@Param("attemptId") attemptId: string) {
    this.handler.cancel(attemptId);
  }

  @Post("validate")
  @HttpCode(200)
  validate(@Body() body: unknown) {
    return this.handler.validate(LINKEDIN_PORTAL_ID, body);
  }

  @Post("read-profile")
  @HttpCode(200)
  readProfile(@Body() body: unknown) {
    return this.handler.readProfile(LINKEDIN_PORTAL_ID, body);
  }
}
