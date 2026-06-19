import { Injectable } from "@nestjs/common";

@Injectable()
export class EmbeddingListener {
  constructor(private readonly embeddingService: EmbeddingService) {}

  @OnEvent('user.created', { async: true })
  async handleUserCreated(payload: EntityIndexedEvent) {
    await this.embeddingService.upsert(payload);
  }
}