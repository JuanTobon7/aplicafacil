import { StorageMediaType } from "../impl/storage.factory.media.impl";
import { StorageMedia } from "./storage.media";

export abstract class FactoryStorageMedia {
  protected storageMedia!: StorageMedia;
  protected type!: StorageMediaType;
  abstract getAvailableStorageMedia(): Promise<StorageMedia>;
}