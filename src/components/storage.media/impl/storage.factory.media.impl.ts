import { Injectable } from "@nestjs/common";
import { ConfigService } from "@nestjs/config";

import { FactoryStorageMedia } from "../contract/storage.factory.media";
import { StorageMedia } from "../contract/storage.media";
import { StorageS3Cloud } from "./storage.s3";
import { LocalStorageMedia } from "./storage.local";

export enum StorageMediaType {
  LOCAL = "local",
  S3 = "s3",
}

@Injectable()
export class FactoryStorageMediaImpl extends FactoryStorageMedia {
  constructor(
    private readonly configService: ConfigService,
    private readonly storageS3Cloud: StorageS3Cloud,
    private readonly localStorageMedia: LocalStorageMedia,
  ) {
    super();

    this.type = this.configService.getOrThrow<StorageMediaType>("STORAGE_MODE");
  }

  async getAvailableStorageMedia(): Promise<StorageMedia> {
    switch (this.type) {
      case StorageMediaType.LOCAL:
        return this.localStorageMedia;

      case StorageMediaType.S3:
        return this.storageS3Cloud;

      default:
        throw new Error(`Unsupported storage media: ${this.type}`);
    }
  }
}