export abstract class StorageMedia {

  abstract uploadMedia(
    id: string,
    path: string,
    file: Buffer,
    contentType?: string,
  ): Promise<string>;

  abstract getMedia(
    id: string,
  ): Promise<string | null>;

  abstract deleteMedia(
    id: string,
  ): Promise<void>;
}