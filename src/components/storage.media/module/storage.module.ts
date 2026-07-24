import { Module } from "@nestjs/common";
import { ConfigModule } from "@nestjs/config";
import { FactoryStorageMediaImpl } from "../impl/storage.factory.media.impl";
import { StorageS3Cloud } from "../impl/storage.s3";
import { LocalStorageMedia } from "../impl/storage.local";

@Module({
  imports: [ConfigModule],
  providers: [
    FactoryStorageMediaImpl,
    StorageS3Cloud,
    LocalStorageMedia,
  ],
  exports: [FactoryStorageMediaImpl],
})
export class StorageModule {}