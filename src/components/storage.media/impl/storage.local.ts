import { promises as fs } from "fs";
import * as path from "path";

import {
  Injectable,
  InternalServerErrorException,
} from "@nestjs/common";
import { ConfigService } from "@nestjs/config";

import { StorageMedia } from "../contract/storage.media";

@Injectable()
export class LocalStorageMedia extends StorageMedia {
  private readonly uploadPath: string;
  private readonly baseUrl: string;

  constructor(
    private readonly configService: ConfigService,
  ) {
    super();

    this.uploadPath = this.configService.get<string>(
      "LOCAL_STORAGE_PATH",
      "uploads",
    );

    this.baseUrl = this.configService.get<string>(
      "APP_URL",
      "http://localhost:3000",
    );
  }

  async uploadMedia(
    id: string,
    folder: string,
    file: Buffer,
    contentType = "application/octet-stream",
  ): Promise<string> {
    const directory = path.join(this.uploadPath, folder);
    const filePath = path.join(directory, id);

    try {
      await fs.mkdir(directory, { recursive: true });

      await fs.writeFile(filePath, file);

      return `${this.baseUrl}/${this.uploadPath}/${folder}/${id}`;
    } catch (error) {
      throw new InternalServerErrorException(
        "Error uploading file to local storage",
      );
    }
  }

  async getMedia(
    id: string,
  ): Promise<string | null> {
    const filePath = path.join(this.uploadPath, id);

    try {
      await fs.access(filePath);

      return `${this.baseUrl}/${this.uploadPath}/${id}`;
    } catch {
      return null;
    }
  }
}