import { ArgumentsHost, Catch, ExceptionFilter, HttpStatus } from "@nestjs/common";
import type { Response } from "express";
import { ScraperError, type ScraperErrorCode } from "../application/exception/scraper-error";

const STATUS_BY_CODE: Partial<Record<ScraperErrorCode, number>> = {
  ALREADY_CONNECTING: HttpStatus.CONFLICT,
  BROWSER_NOT_READY: HttpStatus.SERVICE_UNAVAILABLE,
  UNKNOWN_PORTAL: HttpStatus.NOT_FOUND,
  SESSION_REQUIRED: HttpStatus.BAD_REQUEST,
  INVALID_REQUEST: HttpStatus.BAD_REQUEST,
  SEARCH_FAILED: HttpStatus.BAD_GATEWAY,
  PORTAL_UNREACHABLE: HttpStatus.BAD_GATEWAY,
};

/** Traduce los errores de la aplicación a HTTP con el cuerpo { code, message }. */
@Catch(ScraperError)
export class ScraperExceptionHandler implements ExceptionFilter<ScraperError> {
  catch(error: ScraperError, host: ArgumentsHost) {
    const response = host.switchToHttp().getResponse<Response>();
    if (response.headersSent) {
      response.end();
      return;
    }
    response
      .status(STATUS_BY_CODE[error.code] ?? HttpStatus.INTERNAL_SERVER_ERROR)
      .json({ code: error.code, message: error.message });
  }
}
