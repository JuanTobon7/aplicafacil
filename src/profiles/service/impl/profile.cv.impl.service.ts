import { Injectable, NotFoundException } from "@nestjs/common";
import { InjectRepository } from "@nestjs/typeorm";
import { Repository } from "typeorm";

import { File } from "buffer";

import { ProfileCvService } from "../contract/profile.cv.service";
import { ProfileResponseDto } from "src/profiles/dto/profile.response.dto";

import { ProfileModel } from "src/profiles/models/profiles.model";
import { CvsModel } from "src/profiles/models/cvs.model";

import { FactoryStorageMedia } from "src/components/storage.media/contract/storage.factory.media";
import { FileInterceptorFactory } from "src/components/storage.media/impl/file.factory.interceptor";

@Injectable()
export class ProfileCvServiceImpl
  implements ProfileCvService
{
  constructor(
    @InjectRepository(ProfileModel)
    private readonly profileRepository: Repository<ProfileModel>,

    private readonly storageFactory: FactoryStorageMedia,
  ) {}

  async uploadCv(
    id: string,
    file: File,
  ): Promise<void> {
    const interceptor =
      FileInterceptorFactory.fromMimeType(file.type);

    const sanitized =
      await interceptor.sanitizeFile(file);

    const buffer = Buffer.from(
      await sanitized.arrayBuffer(),
    );

    const storage =
      this.storageFactory.getAvailableStorageMedia();

    const url = await storage.uploadMedia(
      `${id}.pdf`,
      "cv",
      buffer,
      file.type,
    );

    const profile =
      await this.profileRepository.findOne({
        where: { id },
        relations: {
          cv: true,
        },
      });

    if (!profile) {
      throw new NotFoundException(
        "Profile not found",
      );
    }

    if (!profile.cv) {
      profile.cv = new CvsModel();
    }
    
    profile.cv.filePath = url;
    profile.cv.mimeType = file.type;

    await this.profileRepository.save(profile);
  }

  async getCv(
    id: string,
  ): Promise<string | null> {
    const profile =
      await this.profileRepository.findOne({
        where: { id },
        relations: {
          cv: true,
        },
      });

    if (!profile?.cv) {
      return null;
    }

    return this.storageFactory
      .getAvailableStorageMedia()
      .getMedia(profile.cv.filePath);
  }

  async deleteCv(
    id: string,
  ): Promise<void> {
    const profile =
      await this.profileRepository.findOne({
        where: { id },
        relations: {
          cv: true,
        },
      });

    if (!profile?.cv) {
      return;
    }

    await this.storageFactory
      .getAvailableStorageMedia()
      .deleteMedia(profile.cv.filePath);

    profile.cv = undefined;

    await this.profileRepository.save(profile);
  }

  async extractProfileDataFromCv(
    file: File,
  ): Promise<ProfileResponseDto> {
    const interceptor =
      FileInterceptorFactory.fromMimeType(file.type);

    const sanitized =
      await interceptor.sanitizeFile(file);

    const reduced =
      await interceptor.reduceFile(sanitized);

    const buffer =
      await interceptor.getBufferFromFile(reduced);

    // TODO:
    // Llamar aquí al OCR o IA usando `buffer`

    return new ProfileResponseDto();
  }
}