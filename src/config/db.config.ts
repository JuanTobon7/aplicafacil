import { TypeOrmModuleOptions } from "@nestjs/typeorm";
import { ConfigService } from "@nestjs/config";

export const databaseConfig = (
  configService: ConfigService,
): TypeOrmModuleOptions => {

  console.log("========== DATABASE CONFIG ==========");
  console.log("DB_HOST:", configService.get<string>("DB_HOST"));
  console.log("DB_PORT:", configService.get<number>("DB_PORT"));
  console.log("DB_USERNAME:", configService.get<string>("DB_USERNAME"));
  console.log("DB_PASSWORD:", configService.get<string>("DB_PASSWORD"));
  console.log("DB_NAME:", configService.get<string>("DB_NAME"));
  console.log("NODE_ENV:", configService.get<string>("NODE_ENV"));
  console.log("====================================");

  return {
    type: "postgres",

    host: configService.get<string>("DB_HOST"),

    port: Number(configService.get<string>("DB_PORT")),

    username: configService.get<string>("DB_USERNAME"),

    password: configService.get<string>("DB_PASSWORD"),

    database: configService.get<string>("DB_NAME"),

    autoLoadEntities: true,

    synchronize:
      configService.get<string>("NODE_ENV") !== "production",
  };
};