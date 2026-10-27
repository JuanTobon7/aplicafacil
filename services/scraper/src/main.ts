import "reflect-metadata";
import { Logger } from "@nestjs/common";
import { NestFactory } from "@nestjs/core";
import type { NestExpressApplication } from "@nestjs/platform-express";
import { AppModule } from "./app.module";
import { scraperProperties } from "./infrastructure/config/scraper-properties";

async function bootstrap() {
  const app = await NestFactory.create<NestExpressApplication>(AppModule, { bodyParser: false });
  // El storageState puede pesar cientos de KB
  app.useBodyParser("json", { limit: "5mb" });
  app.enableShutdownHooks();
  await app.listen(scraperProperties.port, scraperProperties.host);
  Logger.log(`scraper escuchando en http://${scraperProperties.host}:${scraperProperties.port}`, "Bootstrap");
}

void bootstrap();
