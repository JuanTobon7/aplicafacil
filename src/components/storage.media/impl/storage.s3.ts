import {
  S3Client,
  PutObjectCommand,
  HeadObjectCommand,
} from '@aws-sdk/client-s3';

import {
  Injectable,
  InternalServerErrorException,
} from '@nestjs/common';

import { ConfigService } from '@nestjs/config';

import { StorageMedia } from '../contract/storage.media';

@Injectable()
export class StorageS3Cloud extends StorageMedia {

  private readonly s3: S3Client;

  private readonly bucket: string;

  constructor(
    private readonly configService: ConfigService,
  ) {
    super();

    this.bucket = this.configService.getOrThrow<string>(
      'AWS_S3_BUCKET',
    );

    this.s3 = new S3Client({
      region: this.configService.getOrThrow<string>(
        'AWS_REGION',
      ),
      credentials: {
        accessKeyId:
          this.configService.getOrThrow<string>(
            'AWS_ACCESS_KEY_ID',
          ),
        secretAccessKey:
          this.configService.getOrThrow<string>(
            'AWS_SECRET_ACCESS_KEY',
          ),
      },
    });
  }

  async uploadMedia(
    id: string,
    path: string,
    file: Buffer,
    contentType = 'application/octet-stream',
  ): Promise<string> {

    const key = `${path}/${id}`;

    try {

      await this.s3.send(
        new PutObjectCommand({
          Bucket: this.bucket,
          Key: key,
          Body: file,
          ContentType: contentType,
        }),
      );

      return `https://${this.bucket}.s3.amazonaws.com/${key}`;

    } catch (error) {

      throw new InternalServerErrorException(
        'Error uploading file to S3',
      );
    }
  }

  async getMedia(
    id: string,
  ): Promise<string | null> {

    try {

      await this.s3.send(
        new HeadObjectCommand({
          Bucket: this.bucket,
          Key: id,
        }),
      );

      return `https://${this.bucket}.s3.amazonaws.com/${id}`;

    } catch {

      return null;
    }
  }
}